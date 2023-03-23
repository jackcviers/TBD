package continuations

import continuations.DefDefTransforms.*
import continuations.Types.flattenTypes
import continuations.jvm.internal.ContinuationImpl
import dotty.tools.dotc.ast.{tpd, TreeTypeMap, Trees}
import dotty.tools.dotc.ast.Trees.*
import dotty.tools.dotc.ast.tpd.*
import dotty.tools.dotc.core.Annotations.Annotation
import dotty.tools.dotc.core.Constants.Constant
import dotty.tools.dotc.core.Contexts.{ctx, Context}
import dotty.tools.dotc.core.{Flags, Names, Scopes, Symbols, Types}
import dotty.tools.dotc.core.NullOpsDecorator.stripNull
import dotty.tools.dotc.core.Flags.*
import dotty.tools.dotc.core.Names.{termName, typeName}
import dotty.tools.dotc.core.NameOps.*
import dotty.tools.dotc.core.StdNames.nme
import dotty.tools.dotc.core.Symbols.*
import dotty.tools.dotc.core.Types.{MethodType, OrType, PolyType, RefinedType, Type}
import dotty.tools.dotc.report
import dotty.tools.dotc.transform.ContextFunctionResults

import scala.annotation.tailrec
import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer
import dotty.tools.dotc.util.Property.Key

object DefDefTransforms extends TreesChecks:

  def transformSuspendContinuation(tree: tpd.ValOrDefDef)(using Context): tpd.Tree =
    tree match {
      case _
          if ReturnsContextFunctionWithSuspendType(tree) && !tree.symbol.isAnonymousFunction =>
        report.logWith(s"new tree:")(transformContinuationWithSuspend(tree))
      case _ if HasSuspendParameter(tree) && !tree.symbol.isAnonymousFunction =>
        report.logWith(s"new tree:")(transformContinuationWithSuspend(tree))
      case _ => report.logWith(s"oldTree:")(tree)
    }

  @tailrec
  def flattenBlock(b: tpd.Tree)(using Context): tpd.Tree =
    b match {
      case bb @ Trees.Block(Nil, _: tpd.Closure) => bb
      case Trees.Block(Nil, bb @ tpd.Block(_, _)) => flattenBlock(bb)
      case b => b
    }

  private class BlockFlattener extends TreeMap {
    override def transform(tree: tpd.Tree)(using Context): tpd.Tree =
      tree match {
        case bb @ Trees.Block(Nil, Trees.Block(Nil, Trees.Closure(_, _, _))) =>
          super.transform(bb)
        case Trees.Block(Nil, bb) =>
          super.transform(bb)
        case t => super.transform(t)
      }
  }

  private def transformContinuationWithSuspend(tree: tpd.ValOrDefDef)(using Context): tpd.Tree =
    def fetchSuspensions =
      SuspensionPoints
        .unapplySeq(report.logWith("tree has no suspension points:")(tree))
        .toList
        .flatten

    def transformSuspensionsSuspendingStateMachine(
        suspensionPoints: List[tpd.Tree],
        suspensionInReturnedValue: Boolean)(using ctx: Context) =
      if suspensionPoints.isEmpty then tree
      else
        val transformedTree =
          transformSuspensionsSuspendingStateMachineAux(
            tree,
            suspensionPoints,
            suspensionInReturnedValue)
        report.logWith("state machine and new defdef:")(transformedTree)

    tree match
      case _ if HasSuspensionNotInReturnedValue(tree) =>
        transformSuspensionsSuspendingStateMachine(fetchSuspensions, false)
      case _ if CallsSuspendContinuation(tree) =>
        fetchSuspensions match
          case suspensionPoint :: Nil if !suspensionPoint.isInstanceOf[tpd.ValDef] =>
            transformSuspendOneContinuationResume(tree, suspensionPoint)
          case suspensionPoints =>
            transformSuspensionsSuspendingStateMachine(suspensionPoints, true)
      case vd: tpd.DefDef if BodyHasSuspensionPoint(vd) =>
        // any suspension that still needs a transformation
        tree match
          case t: tpd.DefDef => cpy.DefDef(t)()
          case t: tpd.ValDef => cpy.ValDef(t)()
      case _ => transformNonSuspending(tree)

  private def generateCompletion(owner: Symbol, returnType: Type)(using Context): Symbol =
    newSymbol(
      owner,
      Names.termName(completionParamName),
      Flags.LocalParam | Flags.SyntheticParam,
      requiredPackage("continuations")
        .requiredType("Continuation")
        .typeRef
        .appliedTo(returnType)
    ).entered

  private def params(tree: tpd.ValOrDefDef, completionSym: Symbol)(
      using Context): List[tpd.ParamClause] = {
    val suspendClazz = requiredClass(suspendFullName)
    val completion: tpd.ValDef = tpd.ValDef(completionSym.asTerm, theEmptyTree)
    val completionPC: tpd.ParamClause = List(completion).asInstanceOf[tpd.ParamClause]

    def isImplicitSuspend(p: Trees.ValDef[Type] | Trees.TypeDef[Type]): Boolean =
      p match {
        case p: Trees.ValDef[Type] =>
          p.typeOpt
            .hasClassSymbol(suspendClazz) && p.symbol.flags.isOneOf(Flags.GivenOrImplicit)
        case t: Trees.TypeDef[Type] =>
          t.typeOpt
            .hasClassSymbol(suspendClazz) && t.symbol.flags.isOneOf(Flags.GivenOrImplicit)
      }

    val newParams = tree match
      case defDef: tpd.DefDef =>
        val transformed =
          TreeTypeMap(
            treeMap = {
              case v: tpd.ValDef if isImplicitSuspend(v) =>
                tpd.EmptyTree
              case t =>
                t
            }
          ).transformParamss(tree.asInstanceOf[tpd.DefDef].paramss).filterNot(_.isEmpty)
        val lastIndex = transformed.size - 1
        transformed.zipWithIndex.map {
          case (l, index) if index == lastIndex => l.appended(completion).asInstanceOf[tpd.ParamClause]
          case (l, _) => l.asInstanceOf[tpd.ParamClause]
        }
      case _: tpd.ValDef => List(List(completionPC)).asInstanceOf[List[tpd.ParamClause]]
    println(s"newParams: $newParams")
    tree match {
      case defDef: tpd.DefDef =>
        println(s"oldParams: ${defDef.paramss}")
      case v: tpd.ValDef if v.symbol.paramSymss.nonEmpty =>
        println(s"oldParams: ${v.symbol.paramSymss}")
    }
    newParams

  }

  private def transformSuspendContinuationBody(
      callSuspensionPoint: tpd.Tree,
      safeContinuationRef: tpd.Tree)(using Context): tpd.Tree = {
    val resumeMethod = safeContinuationRef.select(termName(resumeMethodName))
    val raiseMethod = safeContinuationRef.select(termName(raiseMethodName))

    def isAnonContFunction(t: tpd.Tree): Boolean = t match {
      case t @ Trees.DefDef(_, _, _, _) =>
        t.symbol.exists &&
          t.symbol.isAnonymousFunction &&
          t.symbol.paramSymss.flatten.exists(hasContinuationClass)
      case tree @ Trees.Select(_, name) =>
        tree.symbol.owner.name.show == continuationClassName &&
          (name.show == resumeMethodName || name.show == raiseMethodName)
      case _ => false
    }

    val anonymousContinuationFunctions = callSuspensionPoint.filterSubTrees(isAnonContFunction)

    val continuationReferences = anonymousContinuationFunctions.map(_.symbol.owner)

    val safeContinuationRefSymbols =
      List.fill(anonymousContinuationFunctions.size)(safeContinuationRef.symbol)
    val safeContinuationRefOwners = safeContinuationRefSymbols.map(_.owner)

    val hypothesis = new TreeTypeMap(
      treeMap = {
        case t: tpd.DefDef if t.paramss.exists(_.exists(p => hasContinuationClass(p.symbol))) =>
          t.rhs
        case Trees.Apply(fun, args) if fun.symbol.name.show == shiftName =>
          if (args.isEmpty) {
            tpd.EmptyTree
          } else if (args.size == 1) {
            tpd.Block(List.empty, args.last)
          } else tpd.Block(args.dropRight(1), args.last)
        case Trees.Block(stats, Trees.Closure(_, m, _)) if m.existsSubTree { t =>
              anonymousContinuationFunctions.exists(anon => t.symbol.containsSym(anon.symbol))
            } =>
          if (stats.isEmpty) {
            tpd.EmptyTree
          } else if (stats.size == 1) {
            tpd.Block(List.empty, stats.last)
          } else {
            val flattened = Trees.flatten(stats)
            tpd.Block(flattened.dropRight(1), flattened.last)
          }
        case tree @ Trees.Select(_, name)
            if tree.symbol.owner.name.show == continuationClassName &&
              name.show == resumeMethodName =>
          resumeMethod
        case tree @ Trees.Select(_, name)
            if tree.symbol.owner.name.show == continuationClassName &&
              name.show == raiseMethodName =>
          raiseMethod
        case t =>
          t
      },
      oldOwners = continuationReferences.map(_.owner),
      newOwners = safeContinuationRefOwners,
      substFrom = continuationReferences,
      substTo = safeContinuationRefSymbols)(callSuspensionPoint)

    val leftoverSymbols = hypothesis
      .filterSubTrees { t =>
        val owner = t.symbol.maybeOwner
        owner.isAnonymousFunction &&
        owner.paramSymss.flatten.exists(hasContinuationClass)
      }
      .map(_.symbol.maybeOwner)

    val leftoverReplacementSymbols = List.fill(leftoverSymbols.size)(safeContinuationRef.symbol)
    val leftoverReplocementSymbolOvers = leftoverReplacementSymbols.map(_.owner)

    new BlockFlattener().transform(
      new TreeTypeMap(
        oldOwners = leftoverSymbols,
        newOwners = leftoverReplocementSymbolOvers,
        substFrom = leftoverSymbols,
        substTo = leftoverReplacementSymbols
      )(hypothesis))
  }

  private def createTransformedMethodSymbol(
      parent: Symbol,
      transformedMethodParams: List[tpd.ParamClause],
      returnType: Type,
      owner: Option[Symbol] = None)(using Context) =
    newSymbol(
      owner.getOrElse(parent.owner),
      parent.name.asTermName,
      parent.flags | Flags.Method,
      transformedMethodParams
        .reverse
        .foldLeft(Option.empty[MethodType]) { (acc, paramClause) =>
          transformedMethodParams.map(_.map(_.show))
          println(s"(acc, paramClause): ${(acc.map(_.show), paramClause.map(_.show))}")
          acc
            .map { mt =>
              val newMt = MethodType.fromSymbols(paramClause.map(_.symbol), mt)
              println(s"newMt: ${newMt.show}")
              newMt
            }
            .orElse(Some(MethodType.fromSymbols(paramClause.map(_.symbol), returnType)))
        }
        .getOrElse(MethodType.fromSymbols(List.empty, returnType)),
      parent.privateWithin,
      parent.coord
    ).entered

  private def hasSuspendClass(s: Symbol)(using ctx: Context): Boolean =
    s.info.hasClassSymbol(requiredClass(suspendFullName))

  private def hasContinuationClass(s: Symbol)(using ctx: Context): Boolean =
    s.info.hasClassSymbol(requiredClass(continuationFullName))

  private def matchesNameCoord(v: tpd.Tree, tree: tpd.Tree)(using ctx: Context): Boolean =
    v.symbol.name == tree.symbol.name && v.symbol.coord == tree.symbol.coord

  private def blockOf(stats: List[tpd.Tree])(using Context): tpd.Tree = stats match {
    case Nil => tpd.unitLiteral
    case x :: Nil => x
    case xs => tpd.Block(xs.dropRight(1), xs.last)
  }

  private def removeSuspend(typ: Type, returnValue: Option[Type] = None)(using Context): Type =
    val types = flattenTypes(typ)

    types.foldRight(returnValue.getOrElse(types.last)) {
      case (defn.FunctionOf(args, _, ic, ie), inner) =>
        val argsWithoutSuspend =
          args.filterNot(_.hasClassSymbol(requiredClass(suspendFullName)))

        if (argsWithoutSuspend.nonEmpty)
          defn.FunctionOf(
            args = argsWithoutSuspend,
            resultType = inner,
            isContextual = ic,
            isErased = ie)
        else
          inner
      case (RefinedType(p, n, PolyType(ptp, mt @ MethodType(mtp))), inner) =>
        RefinedType(
          parent = p,
          name = n,
          info = PolyType.fromParams(
            params = ptp,
            resultType = MethodType(paramNames = mtp)(
              paramInfosExp = _ => mt.paramInfos,
              resultTypeExp = _ => inner)))
      case (_, inner) =>
        inner
    }

  private def removeSuspendReturnAny(typ: Type)(using Context): Type =
    removeSuspend(typ, Some(ref(defn.AnyType).tpe))

  private def getReturnTypeBodyContextFunctionOwner(tree: tpd.ValOrDefDef)(
      using Context): (Type, tpd.Tree, Option[Symbol]) =
    if (ReturnsContextFunctionWithSuspendType(tree))
      val returnType = removeSuspend(tree.tpt.tpe)

      val (rhs, contextFunctionOwner) = tree.rhs match
        case tpd.Block(List(d @ tpd.DefDef(_, _, _, _)), _) if d.symbol.isAnonymousFunction =>
          (d.rhs, Option(d.symbol))
        case rhs => (rhs, Option.empty)

      (returnType, rhs, contextFunctionOwner)
    else (tree.tpt.tpe, tree.rhs, Option.empty)

  private def deleteOldSymbol(sym: Symbol)(using Context): Unit =
    sym.enclosingClass.asClass.delete(sym)

  private def transformSuspendOneContinuationResume(
      tree: tpd.ValOrDefDef,
      suspensionPoint: tpd.Tree)(using Context): tpd.DefDef =

    val continuationTraitSym: ClassSymbol = requiredClass("continuations.Continuation")
    val continuationObjectSym: Symbol = continuationTraitSym.companionModule

    val parent: Symbol = tree.symbol

    val (returnType, rhs, contextFunctionOwner) =
      getReturnTypeBodyContextFunctionOwner(tree)

    val completion = generateCompletion(parent, returnType)

    val transformedMethodParams = params(tree, completion)

    val transformedMethodSymbol = {
      val suspendedState =
        ref(continuationObjectSym).select(termName("State")).select(termName("Suspended"))

      val finalMethodReturnType: tpd.TypeTree =
        tpd.TypeTree(
          OrType(
            OrType(ctx.definitions.AnyType, ctx.definitions.NullType, soft = false),
            suspendedState.symbol.namedType,
            soft = false)
        )
      createTransformedMethodSymbol(parent, transformedMethodParams, finalMethodReturnType.tpe)
    }

    deleteOldSymbol(parent)

    val transformedMethod =
      tpd.DefDef(sym = transformedMethodSymbol)

    val transformedMethodCompletionParam = ref(
      transformedMethod.termParamss.flatten.find(_.symbol.denot.matches(completion)).get.symbol)

    val continuation1: tpd.ValDef =
      val continuationTyped: Type =
        continuationTraitSym.typeRef.appliedTo(returnType)
      tpd.ValDef(
        sym = newSymbol(
          transformedMethodSymbol,
          termName("continuation1"),
          Flags.Local,
          continuationTyped).entered,
        rhs = transformedMethodCompletionParam
      )

    /*
     ```
     val safeContinuation: continuations.SafeContinuation[Int] =
       new continuations.SafeContinuation[Int](
         continuations.intrinsics.IntrinsicsJvm$package.intercepted[Int]($continuation)(),
         continuations.Continuation.State.Undecided
       )
     ```
     */
    val safeContinuation: tpd.ValDef = {
      val constructor =
        ref(requiredModule("continuations.SafeContinuation"))
          .select(termName("init"))
          .appliedToType(returnType)
          .appliedTo(ref(continuation1.symbol))

      val sym: TermSymbol = newSymbol(
        transformedMethodSymbol,
        termName("safeContinuation"),
        Flags.Local,
        constructor.tpe).entered

      tpd.ValDef(sym, constructor)
    }

    val safeContinuationRef =
      ref(safeContinuation.symbol)

    val callSuspensionPoint: tpd.Tree =
      suspensionPoint match
        case tpd.Inlined(call, _, _) =>
          new TreeTypeMap(
            oldOwners = List(parent),
            newOwners = List(transformedMethod.symbol),
            substFrom = List(parent),
            substTo = List(transformedMethod.symbol)
          ).transform(call)
        case _ => tpd.EmptyTree

    val shiftBody =
      transformSuspendContinuationBody(callSuspensionPoint, safeContinuationRef)

    val continuationBlock = tpd.Block(
      List(continuation1, safeContinuation) ++ List(shiftBody),
      safeContinuationRef.select(termName("getOrThrow")).appliedToNone
    )

    val transformedMethodParamSymbols: List[Symbol] =
      transformedMethodSymbol.paramSymss.flatMap(_.filterNot(hasContinuationClass))

    val oldMethodParamSymbols: List[Symbol] =
      tree.symbol.paramSymss.flatMap(_.filterNot(s => hasSuspendClass(s) || s.isTypeParam))

    val substituteContinuation = new TreeTypeMap(
      treeMap = {
        case tree if treeCallsSuspend(tree) => continuationBlock
        case tree => tree
      },
      substFrom = List(parent) ++ oldMethodParamSymbols ++ contextFunctionOwner.toList,
      substTo = List(transformedMethod.symbol) ++ transformedMethodParamSymbols ++
        List(transformedMethod.symbol),
      oldOwners = List(parent) ++ contextFunctionOwner.toList,
      newOwners = List(transformedMethod.symbol, transformedMethod.symbol)
    )
    cpy.DefDef(transformedMethod)(rhs = substituteContinuation.transform(rhs))
  end transformSuspendOneContinuationResume

  private def transformNonSuspending(tree: tpd.ValOrDefDef)(using ctx: Context): tpd.Tree =
    val parent = tree.symbol

    val (methodReturnType, _, _) =
      getReturnTypeBodyContextFunctionOwner(tree)

    val completion =
      generateCompletion(parent, Types.OrType(methodReturnType, ctx.definitions.AnyType, false))

    val transformedMethodParams = params(tree, completion)

    val transformedMethodSymbol =
      createTransformedMethodSymbol(
        parent,
        transformedMethodParams,
        removeSuspendReturnAny(methodReturnType)
      )

    deleteOldSymbol(parent)

    val transformedMethod =
      tpd.DefDef(sym = transformedMethodSymbol)

    val transformedMethodParamSymbols: List[Symbol] =
      transformedMethodSymbol.paramSymss.flatMap(_.filterNot(hasContinuationClass))

    val oldMethodParamSymbols: List[Symbol] =
      parent.paramSymss.flatten.filterNot(s => hasSuspendClass(s) || s.isTypeParam)

    val newAnonFunctions = ListBuffer(transformedMethod.symbol)
    val substituteContinuation = new TreeTypeMap(
      treeMap = {
        case defdef: tpd.DefDef if defdef.symbol.isAnonymousFunction =>
          val tpt: Type = removeSuspendReturnAny(defdef.tpt.tpe)

          val params: List[tpd.ParamClause] = new TreeTypeMap(treeMap = {
            case p: tpd.ValDef if hasSuspendClass(p.symbol) =>
              tpd.EmptyTree
            case t => t
          }).transformParamss(defdef.paramss)

          val newMethodOwner: Option[Symbol] =
            if (defdef.symbol.owner.is(Method) &&
              defdef.symbol.owner.name.matchesTargetName(nme.apply) &&
              defdef.symbol.owner.owner.isAnonymousClass &&
              defdef.symbol.owner.owner.info.parents.exists {
                _.hasClassSymbol(defn.PolyFunctionClass)
              })
              Some(defdef.symbol.owner)
            else newAnonFunctions.lastOption

          val newMethodSymbol =
            createTransformedMethodSymbol(
              defdef.symbol,
              params,
              tpt,
              newMethodOwner
            )

          if (params.isEmpty) defdef.rhs
          else
            newAnonFunctions.append(newMethodSymbol)

            def fillFlags(s: Symbol): Symbol = {
              val existingFlags =
                params
                  .flatten
                  .find(_.name == s.name)
                  .map(_.symbol.flags)
                  .getOrElse(Flags.EmptyFlags)
              s.setFlag(existingFlags)
              s
            }

            val methodParams = newMethodSymbol.paramSymss.map(_.map(fillFlags))

            new TreeTypeMap(
              oldOwners = List(defdef.symbol),
              newOwners = List(newMethodSymbol),
              substFrom = params.flatMap(_.map(_.symbol)),
              substTo = methodParams.flatten
            ).transform(
              tpd.DefDef(
                sym = newMethodSymbol.asTerm,
                paramss = methodParams,
                resultType = newMethodSymbol.info.resultType,
                rhs = defdef.rhs
              ))
        case tpd.Block(List(defdef: tpd.DefDef), _)
            if defdef.symbol.isAnonymousFunction &&
              defdef.paramss.forall(_.forall(x => hasSuspendClass(x.symbol))) =>
          new TreeTypeMap(
            oldOwners = List(defdef.symbol),
            newOwners = newAnonFunctions.lastOption.toList
          ).transform(defdef.rhs)
        case c: tpd.Closure =>
          // Any param of an anonymous function matches a param of the Closure
          def clashesParam(anonFun: Symbol): Boolean =
            anonFun
              .paramSymss
              .exists(_.exists(s => c.meth.symbol.paramSymss.exists(_.exists(_.matches(s)))))
          newAnonFunctions.find(clashesParam) match {
            case None => c
            case Some(anon) => tpd.Closure(c.env, ref(anon), c.tpt)
          }
        case t => t
      },
      substFrom = List(parent) ++ oldMethodParamSymbols,
      substTo = List(transformedMethod.symbol) ++ transformedMethodParamSymbols,
      oldOwners = List(parent),
      newOwners = List(transformedMethod.symbol)
    )

    val transformedDefDef =
      cpy.DefDef(transformedMethod)(rhs = substituteContinuation.transform(tree.rhs))

    ContextFunctionResults.annotateContextResults(transformedDefDef)
    transformedDefDef

  /* The representation for the following code:
   ```
     case _: Int => throw new IllegalArgumentException("call to 'resume' before 'invoke' with coroutine")
   ```
   */
  private def resumeDefaultCaseError(using Context): tpd.CaseDef = {
    val IllegalArgumentExceptionClass_stringConstructor: TermSymbol =
      defn
        .IllegalArgumentExceptionClass
        .info
        .member(nme.CONSTRUCTOR)
        .suchThat(_.info.firstParamTypes match {
          case List(pt) =>
            pt.stripNull.isRef(defn.StringClass)
          case _ => false
        })
        .symbol
        .asTerm

    tpd.CaseDef(
      tpd.Underscore(defn.IntType),
      tpd.EmptyTree,
      tpd.Throw(
        tpd.New(
          defn.IllegalArgumentExceptionType,
          IllegalArgumentExceptionClass_stringConstructor,
          List(tpd.Literal(Constant("call to 'resume' before 'invoke' with coroutine")))
        )
      )
    )
  }

  private def transformSuspensionsSuspendingStateMachineAux(
      tree: tpd.ValOrDefDef,
      suspensionPoints: List[tpd.Tree],
      suspensionInReturnedValue: Boolean)(using ctx: Context): tpd.Thicket =
    val suspensionPointsVal: List[tpd.Tree] =
      suspensionPoints.collect { case vd: tpd.ValDef => vd }
    val suspensionPointsSize = suspensionPoints.size

    val continuationClassRef = requiredClassRef(continuationFullName)
    val continuationModule = requiredModule(continuationFullName)
    val safeContinuationClass = requiredClass("continuations.SafeContinuation")
    val continuationImplClass = requiredClass("continuations.jvm.internal.ContinuationImpl")
    val baseContinuationImplClassRef = requiredClassRef(
      "continuations.jvm.internal.BaseContinuationImpl")

    val parent = tree.symbol
    val treeOwner = parent.owner
    val defName = parent.name

    val (returnType, rhs, contextFunctionOwner) =
      getReturnTypeBodyContextFunctionOwner(tree)

    val suspendedState =
      ref(continuationModule).select(termName("State")).select(termName("Suspended"))

    val suspendedType: Types.NamedType = suspendedState.symbol.namedType

    val integerOR =
      defn.IntClass.requiredMethod(nme.OR, List(defn.IntType))
    val integerAND =
      defn.IntClass.requiredMethod(nme.AND, List(defn.IntType))
    val integerNE =
      defn.IntClass.requiredMethod(nme.NE, List(defn.IntType))
    val integerMin =
      ref(requiredModuleRef("scala.Int").select(Names.termName("MinValue")).symbol)

    val intType = defn.IntType
    val anyType = defn.AnyType
    val anyOrNullType = Types.OrNull(anyType)
    val anyNullSuspendedType =
      Types.OrType(Types.OrNull(defn.AnyType), suspendedType, false)

    val stateMachineContinuationClassName = s"${treeOwner.name.show}$$$defName$$1"

    val continuationsStateMachineSymbol = newCompleteClassSymbol(
      treeOwner,
      typeName(stateMachineContinuationClassName),
      SyntheticArtifact,
      List(continuationImplClass.typeRef),
      Scopes.newScope
    ).entered.asClass

    val completionParamName = termName("$completion")
    val resultVarName = termName("$result")
    val labelVarParam = termName("$label")

    val continuationsStateMachineConstructor = {
      val symbol = newConstructor(
        continuationsStateMachineSymbol,
        Flags.Synthetic,
        List(completionParamName),
        List(continuationClassRef.appliedTo(anyOrNullType))
      ).entered.asTerm

      tpd.DefDef(symbol)
    }

    val eitherThrowableAnyNullSuspendedType =
      requiredClassRef("scala.util.Either").appliedTo(
        defn.ThrowableType,
        anyNullSuspendedType
      )

    val continuationStateMachineResult = tpd.ValDef(
      newSymbol(
        continuationsStateMachineSymbol,
        resultVarName,
        Flags.Synthetic | Flags.Mutable,
        eitherThrowableAnyNullSuspendedType).entered,
      Underscore(eitherThrowableAnyNullSuspendedType)
    )

    val continuationStateMachineLabel = tpd.ValDef(
      newSymbol(
        continuationsStateMachineSymbol,
        labelVarParam,
        Flags.Synthetic | Flags.Mutable,
        intType).entered,
      Underscore(intType)
    )

    val continuationsStateMachineThis: tpd.This =
      tpd.This(continuationsStateMachineSymbol)

    val continuationsStateMachineLabelSelect =
      continuationsStateMachineThis.select(labelVarParam)

    val completion = generateCompletion(parent, returnType)

    val transformedMethodParams = params(tree, completion)

    val newReturnType =
      Types.OrType(Types.OrNull(returnType), suspendedState.symbol.namedType, false)
    val transformedMethodSymbol =
      createTransformedMethodSymbol(parent, transformedMethodParams, newReturnType)

    deleteOldSymbol(parent)

    val transformedMethod: tpd.DefDef =
      tpd.DefDef(sym = transformedMethodSymbol)

    val transformedMethodCompletionParam =
      println(s"transformedMethod.termParamss: ${transformedMethod.termParamss}")
      ref(
        transformedMethod
          .termParamss
          .flatten
          .find(_.symbol.denot.matches(completion))
          .get
          .symbol)

    val transformedMethodParamsWithoutCompletion =
      transformedMethod.termParamss.flatten.filterNot(_.symbol.denot.matches(completion))

    val numTransformedMethodOriginalParams =
      transformedMethod.termParamss.map(_.length).sum - 1

    val $completion = continuationsStateMachineConstructor
      .termParamss
      .flatten
      .find(_.name.matchesTargetName(completionParamName))
      .get
      .symbol

    val continuationStateMachineResultSetter =
      newSymbol(
        continuationsStateMachineSymbol,
        continuationStateMachineResult.symbol.asTerm.name.setterName,
        Method | Flags.Accessor,
        info = MethodType(
          continuationStateMachineResult.symbol.asTerm.info.widenExpr :: Nil,
          defn.UnitType)
      ).entered

    val continuationStateMachineLabelSetter =
      newSymbol(
        continuationsStateMachineSymbol,
        continuationStateMachineLabel.symbol.asTerm.name.setterName,
        Method | Flags.Accessor,
        info = MethodType(
          continuationStateMachineLabel.symbol.asTerm.info.widenExpr :: Nil,
          defn.UnitType)
      ).entered

    val (nonDefDefRowsBeforeSuspensionPoint, rowsAfterLastSuspensionPoint)
        : (Map[tpd.Tree, List[tpd.Tree]], List[tpd.Tree]) =
      rhs
        .toList
        .flatMap {
          case Trees.Block(trees, tree) => trees :+ tree
          case tree => List(tree)
        }
        .foldLeft((0, Map.empty[tpd.Tree, List[tpd.Tree]], List.empty[tpd.Tree])) {
          case ((suspensionPointsCounter, nonDefDefRowsBefore, rowsAfterLast), row) =>
            if (treeCallsSuspend(row) || valDefTreeCallsSuspend(row))
              val rowsBefore =
                row match
                  case _: tpd.DefDef =>
                    nonDefDefRowsBefore
                  case _ =>
                    nonDefDefRowsBefore.updatedWith(suspensionPoints(suspensionPointsCounter)) {
                      case None => Option(List.empty)
                      case rows => rows
                    }

              (suspensionPointsCounter + 1, rowsBefore, rowsAfterLast)
            else if (suspensionPointsCounter < suspensionPointsSize)
              val rowsBefore =
                row match
                  case _: tpd.DefDef =>
                    nonDefDefRowsBefore
                  case _ =>
                    nonDefDefRowsBefore.updatedWith(suspensionPoints(suspensionPointsCounter)) {
                      case Some(ll) => Option(ll :+ row)
                      case None => Option(row :: Nil)
                    }

              (suspensionPointsCounter, rowsBefore, rowsAfterLast)
            else (suspensionPointsCounter, nonDefDefRowsBefore, row :: rowsAfterLast)
        }
        .drop(1)

    def toTreeBeforeSuspend(suspend: tpd.Tree, rows: List[tpd.Tree], i: Int): List[tpd.Tree] = {
      val rowsAfter =
        nonDefDefRowsBeforeSuspensionPoint.drop(i + 1).values.toList.flatten ++
          nonDefDefRowsBeforeSuspensionPoint.drop(i + 1).keySet ++
          rowsAfterLastSuspensionPoint

      (rows :+ suspend).collect { case vd: tpd.ValDef => vd }.flatMap { vd =>
        rowsAfter.flatMap(_.shallowFold(List.empty[tpd.Tree]) {
          case (usedTrees, Trees.Inlined(call, _, _)) =>
            call.filterSubTrees(_.symbol.coord == vd.symbol.coord) ++ usedTrees
          case (usedTrees, tree) =>
            if (tree.symbol.coord == vd.symbol.coord) tree :: usedTrees
            else usedTrees
        })
      }
    }

    val treesBeforeSuspendUsedAfterwards: List[tpd.Tree] =
      nonDefDefRowsBeforeSuspensionPoint
        .toList
        .zipWithIndex
        .flatMap { case ((suspend, rows), i) => toTreeBeforeSuspend(suspend, rows, i) }
        .distinctBy(_.symbol.coord)

    def toGlobalVar(tree: tpd.Tree): tpd.ValDef =
      tpd.ValDef(
        newSymbol(
          transformedMethod.symbol,
          tree.symbol.asTerm.name,
          Local | Mutable | Synthetic,
          tree.symbol.info,
          coord = tree.symbol.coord).entered,
        nullLiteral
      )

    val globalVars: List[tpd.Tree] =
      (treesBeforeSuspendUsedAfterwards ++ suspensionPointsVal)
        .distinctBy(_.symbol.coord)
        .map(toGlobalVar)

    val transformedMethodParamsAsVals: List[tpd.Tree] =
      transformedMethodParamsWithoutCompletion.collect {
        case vd: tpd.ValDef =>
          tpd.ValDef(
            newSymbol(
              transformedMethod.symbol,
              termName(vd.name.toString + "##1"),
              Local | Mutable | Synthetic,
              vd.symbol.info,
              coord = vd.symbol.coord
            ).entered,
            ref(vd.symbol)
          )
      }

    def contFsmI(i: Int): tpd.ValDef =
      tpd.ValDef(
        newSymbol(
          continuationsStateMachineSymbol,
          termName(s"I$$$i"),
          Flags.Synthetic | Flags.Mutable,
          anyType).entered,
        Underscore(anyType))

    val continuationStateMachineI$Ns = {
      val numVal = transformedMethodParamsAsVals.size + globalVars.size
      Range(0, numVal).toList.map(contFsmI)
    }

    val continuationStateMachineClass: tpd.TypeDef =

      def toContFsmSetter(vd: tpd.ValDef): tpd.DefDef = {
        val sym = newSymbol(
          continuationsStateMachineSymbol,
          vd.symbol.asTerm.name.setterName,
          Method | Flags.Accessor,
          info = MethodType(vd.symbol.asTerm.info.widenExpr :: Nil, defn.UnitType)
        ).entered
        tpd.DefDef(sym, tpd.unitLiteral)
      }

      val invokeSuspendSymbol =
        newSymbol(
          continuationsStateMachineSymbol,
          Names.termName("invokeSuspend"),
          Flags.Override | Flags.Protected | Flags.Method,
          MethodType(
            List(termName("result")),
            List(eitherThrowableAnyNullSuspendedType),
            anyOrNullType
          )
        ).entered.asTerm

      val invokeSuspendMethod = tpd.DefDef(
        invokeSuspendSymbol,
        paramss =>
          tpd.Block(
            List(
              tpd
                .Assign(continuationsStateMachineThis.select(resultVarName), paramss.head.head),
              tpd.Assign(
                continuationsStateMachineLabelSelect,
                continuationsStateMachineLabelSelect.select(integerOR).appliedTo(integerMin)
              )
            ),
            ref(transformedMethod.symbol).appliedToTermArgs(
              List.fill(numTransformedMethodOriginalParams)(nullLiteral) :+
                continuationsStateMachineThis
            )
          )
      )

      val createSymbol =
        newSymbol(
          continuationsStateMachineSymbol,
          Names.termName("create"),
          Flags.Override | Flags.Method,
          MethodType(
            List(termName("value"), termName("completion")),
            List(anyOrNullType, continuationClassRef.appliedTo(anyOrNullType)),
            continuationClassRef.appliedTo(defn.UnitType)
          )
        ).entered.asTerm

      val createMethod = tpd.DefDef(
        createSymbol,
        paramss =>
          tpd
            .New(baseContinuationImplClassRef)
            .select(nme.CONSTRUCTOR)
            .appliedTo(paramss.head.drop(1).head)
      )

      val invokeSymbol =
        newSymbol(
          continuationsStateMachineSymbol,
          Names.termName("invoke"),
          Flags.Protected | Flags.Method,
          MethodType(
            List(termName("p1"), termName("p2")),
            List(anyOrNullType, continuationClassRef.appliedTo(anyOrNullType)),
            anyOrNullType
          )
        ).entered.asTerm

      val invokeMethod = tpd.DefDef(
        invokeSymbol,
        paramss =>
          val newContinuation = ref(createMethod.symbol).appliedToTermArgs(
            List(paramss.head.head, paramss.head.drop(1).head)
          )
          val dummy = tpd
            .New(requiredClassRef("scala.util.Right"))
            .select(nme.CONSTRUCTOR)
            .appliedToTypes(
              List(defn.UnitType, defn.UnitType)
            )
            .appliedTo(
              tpd.Literal(Constant(()))
            )

          newContinuation
            .select(nme.asInstanceOf_)
            .appliedToType(baseContinuationImplClassRef.symbol.thisType)
            .select(termName("invokeSuspend"))
            .appliedTo(
              dummy
            )
      )

      val extendsContImpl: tpd.Tree =
        tpd
          .New(ref(continuationImplClass))
          .select(nme.CONSTRUCTOR)
          .appliedTo(
            ref($completion),
            ref($completion).select(termName("context"))
          )

      ClassDefWithParents(
        cls = continuationsStateMachineSymbol,
        constr = continuationsStateMachineConstructor,
        parents = List(extendsContImpl),
        body = List(
          continuationStateMachineI$Ns,
          continuationStateMachineI$Ns.map(toContFsmSetter),
          List(
            continuationStateMachineResult,
            continuationStateMachineLabel,
            tpd.DefDef(continuationStateMachineResultSetter, tpd.unitLiteral),
            tpd.DefDef(continuationStateMachineLabelSetter, tpd.unitLiteral),
            invokeSuspendMethod,
            createMethod,
            invokeMethod
          )
        ).flatten
      )
    end continuationStateMachineClass

    def transformSuspendTree(newParent: Symbol) =
      val contSymbol: TermSymbol =
        newSymbol(
          newParent,
          termName("$continuation"),
          Local | Synthetic,
          continuationStateMachineClass.tpe).entered

      val completionMatch = {
        /* ```
         case x$0: ${contFsmClass} if x$0.$label & Integer.MinValue != 0 =>
           x$0.$label = x$0.label - Integer.MinValue
           x$0
         ```*/
        val case11: tpd.CaseDef = {
          val param =
            newSymbol(
              newParent,
              nme.x_0,
              Flags.Case | Flags.CaseAccessor,
              continuationStateMachineClass.tpe
            ).entered

          val paramLabel = ref(param).select(labelVarParam)

          tpd.CaseDef(
            tpd.Bind(param, tpd.Typed(ref(param), ref(continuationsStateMachineSymbol))),
            ref(param)
              .select(labelVarParam)
              .select(integerAND)
              .appliedTo(integerMin)
              .select(integerNE)
              .appliedTo(tpd.Literal(Constant(0x0))),
            tpd.Block(
              List(tpd.Assign(paramLabel, paramLabel.select(defn.Int_-).appliedTo(integerMin))),
              ref(param)
            )
          )
        }
        /* ```
         case _ => new ${contFsmClass}($completion)
        ```*/
        val case12 = tpd.CaseDef(
          Underscore(anyType),
          tpd.EmptyTree,
          tpd
            .New(tpd.TypeTree(continuationStateMachineClass.tpe))
            .select(nme.CONSTRUCTOR)
            .appliedTo(transformedMethodCompletionParam)
        )
        tpd.Match(transformedMethodCompletionParam, List(case11, case12))
      }

      val resultSym: TermSymbol =
        newSymbol(
          newParent,
          termName("$result"),
          Local,
          eitherThrowableAnyNullSuspendedType).entered

      val callToCheckResult =
        ref(requiredModule(continuationFullName))
          .select(termName("checkResult"))
          .appliedTo(ref(resultSym))

      val labels: List[Symbol] =
        nonDefDefRowsBeforeSuspensionPoint.keySet.toList.indices.toList.map { i =>
          newSymbol(newParent, termName(s"label$i"), Flags.Label, defn.UnitType).entered
        }

      def paramsAndRowsBeforeSuspendIncludingSuspend(index: Int) =
        transformedMethodParamsAsVals ++
          nonDefDefRowsBeforeSuspensionPoint
            .take(index + 1)
            .transform { (suspend, rowsBefore) => rowsBefore :+ suspend }
            .values
            .toList
            .flatten

      def treesToAssignToI$Ns(
          paramsAndRowsBeforeSuspendIncludingSuspend: List[tpd.Tree]): List[tpd.Tree] =
        (transformedMethodParamsAsVals ++ globalVars)
          .filter { v =>
            paramsAndRowsBeforeSuspendIncludingSuspend
              .dropRight(1)
              .exists(_.symbol.denot.matches(v.symbol))
          }
          .sortWith { (t1, t2) =>
            paramsAndRowsBeforeSuspendIncludingSuspend.indexOf(t1) >
              paramsAndRowsBeforeSuspendIncludingSuspend.indexOf(t2)
          }

      def toCase(suspension: tpd.Tree, rowsBefore: List[tpd.Tree], i: Int): tpd.CaseDef = {
        val label =
          if (i > 0) tpd.Labeled(labels(i).asTerm, tpd.EmptyTree) else tpd.EmptyTree

        val callSuspensionPoint: tpd.Tree = suspension match
          case vd: tpd.ValDef if vd.rhs.isInstanceOf[tpd.Inlined] =>
            new TreeTypeMap(
              oldOwners = List(parent),
              newOwners = List(newParent),
              substFrom = List(parent),
              substTo = List(newParent)
            ).transform(vd.rhs.asInstanceOf[tpd.Inlined].call)
          case tpd.Inlined(call, _, _) =>
            new TreeTypeMap(
              oldOwners = List(parent),
              newOwners = List(newParent),
              substFrom = List(parent),
              substTo = List(newParent)
            ).transform(call)
          case _ => tpd.EmptyTree

        val safeContinuation: tpd.ValDef = {
          val shiftType = callSuspensionPoint.tpe

          val safeContinuationConstructor =
            ref(requiredModule("continuations.SafeContinuation"))
              .select(termName("init"))
              .appliedToType(shiftType)
              .appliedTo(ref(contSymbol))

          tpd.ValDef(
            newSymbol(
              newParent,
              termName("safeContinuation"),
              Flags.Local,
              safeContinuationConstructor.tpe).entered,
            safeContinuationConstructor)
        }

        val safeContinuationRef = ref(safeContinuation.symbol)

        val shiftBody: tpd.Tree =
          transformSuspendContinuationBody(callSuspensionPoint, safeContinuationRef)

        def assignGlobalVarResult(vd: tpd.ValDef): tpd.Assign =
          tpd.Assign(
            globalVars.find(matchesNameCoord(_, vd)).get,
            ref(resultSym).select(nme.asInstanceOf_).appliedToType(vd.symbol.info)
          )

        val assignResultToGlobalVar =
          nonDefDefRowsBeforeSuspensionPoint.keySet.toList.lift(i - 1) match
            case Some(vd: tpd.ValDef) => assignGlobalVarResult(vd)
            case _ => tpd.EmptyTree

        val paramsAndRowsBeforeSuspendIncludingSuspendCurrent =
          paramsAndRowsBeforeSuspendIncludingSuspend(i)

        def assignToI(tree: tpd.Tree, i: Int): tpd.Assign =
          tpd.Assign(
            ref(contSymbol).select(continuationStateMachineI$Ns(i).symbol),
            ref(tree.symbol))

        val assignToI$Ns =
          treesToAssignToI$Ns(paramsAndRowsBeforeSuspendIncludingSuspendCurrent)
            .zipWithIndex
            .map(assignToI)

        val relevantParamsAndRows =
          paramsAndRowsBeforeSuspendIncludingSuspendCurrent
            .reverse
            .drop(1)
            .dropWhile(r => !treeCallsSuspend(r) && !valDefTreeCallsSuspend(r))
            .drop(1)

        def keepAssign(assign: tpd.Assign): Boolean =
          relevantParamsAndRows.exists(_.symbol.denot.matches(assign.lhs.symbol))

        val assignFromI$Ns: List[Trees.Assign[Type]] =
          assignToI$Ns.map(a => tpd.Assign(a.rhs, a.lhs)).filter(keepAssign)

        def updateForGlobalVars(tree: tpd.Tree): tpd.Tree =
          tree match
            case vd: tpd.ValDef =>
              globalVars
                .find(_.symbol.denot.matches(vd.symbol))
                .fold(vd)(gv => tpd.Assign(ref(gv.symbol), vd.rhs))
            case _ => tree

        val incrementLabel =
          tpd.Assign(ref(contSymbol).select(labelVarParam), tpd.Literal(Constant(i + 1)))

        val orThrowMatch: tpd.Match = {
          val orThrowSymbol: TermSymbol =
            newSymbol(
              newParent,
              termName("orThrow"),
              Flags.Case | Flags.CaseAccessor,
              anyNullSuspendedType).entered

          val assignGetOrThrowToGlobalVar =
            nonDefDefRowsBeforeSuspensionPoint.keySet.toList(i) match
              case vd: tpd.ValDef =>
                tpd.Assign(
                  globalVars.find(matchesNameCoord(_, vd)).get,
                  ref(orThrowSymbol).select(nme.asInstanceOf_).appliedToType(vd.symbol.info)
                ) :: Nil
              case _ =>
                Nil

          val returnToLabel =
            if (suspensionPointsSize > 1 && i < suspensionPointsSize - 1)
              List(tpd.Return(unitLiteral, labels(i + 1)))
            else
              Nil

          val resultValue =
            if (i == suspensionPointsSize - 1 && suspensionInReturnedValue)
              List(ref(orThrowSymbol))
            else Nil

          val sts = List(assignGetOrThrowToGlobalVar, returnToLabel, resultValue).flatten
          tpd.Match(
            ref(safeContinuation.symbol).select(termName("getOrThrow")).appliedToNone,
            List(
              tpd.CaseDef(suspendedState, tpd.EmptyTree, tpd.Return(suspendedState, newParent)),
              tpd.CaseDef(tpd.Bind(orThrowSymbol, tpd.EmptyTree), tpd.EmptyTree, blockOf(sts))
            )
          )
        }

        val stats: List[tpd.Tree] = List(
          assignFromI$Ns,
          List(callToCheckResult),
          List(assignResultToGlobalVar),
          List(label),
          rowsBefore.map(updateForGlobalVars),
          assignToI$Ns,
          List(incrementLabel),
          List(safeContinuation),
          List(shiftBody),
          List(orThrowMatch)
        ).flatten

        tpd.CaseDef(tpd.Literal(Constant(i)), tpd.EmptyTree, blockOf(stats))
      }

      val cases =
        nonDefDefRowsBeforeSuspensionPoint.zipWithIndex.toList.map {
          case ((suspension, rowsBefore), i) => toCase(suspension, rowsBefore, i)
        }

      val lastStatement = suspensionPoints.lastOption match {
        case Some(vd: tpd.ValDef) =>
          tpd.Assign(
            globalVars.find(matchesNameCoord(_, vd)).get,
            ref(resultSym).select(nme.asInstanceOf_).appliedToType(vd.symbol.info)
          ) :: Nil
        case Some(_) if suspensionInReturnedValue => ref(resultSym) :: Nil
        case _ => Nil
      }

      val stats =
        treesToAssignToI$Ns {
          paramsAndRowsBeforeSuspendIncludingSuspend(suspensionPoints.size - 1)
        }.zipWithIndex.map { (tree, i) =>
          tpd.Assign(
            ref(tree.symbol),
            ref(contSymbol).select(continuationStateMachineI$Ns(i).symbol)
          )
        } ++
          List(callToCheckResult) ++ lastStatement

      val block = tpd.Block(stats.dropRight(1), stats.last)
      val case2BeforeDefault = tpd.CaseDef(
        tpd.Literal(Constant(suspensionPointsSize)),
        tpd.EmptyTree,
        block
      )

      val labelMatch = tpd
        .Match(
          ref(contSymbol).select(labelVarParam),
          cases ++ List(case2BeforeDefault, resumeDefaultCaseError)
        )
        .withType(anyNullSuspendedType)

      tpd.Block(
        List(
          tpd.ValDef(contSymbol, completionMatch),
          tpd.ValDef(resultSym, ref(contSymbol).select(resultVarName))),
        labelMatch)
    end transformSuspendTree

    val transformedMethodParamSymbols: List[Symbol] =
      transformedMethodSymbol.paramSymss.flatMap(_.filterNot(hasContinuationClass))

    val oldMethodParamSymbols: List[Symbol] =
      tree.symbol.paramSymss.flatten.filterNot(s => hasSuspendClass(s) || s.isTypeParam)

    /**
     * If there are more than one Suspension points we create the whole state machine for all
     * the points and replace the last occurrence of `shift`. The other occurrences are not
     * needed and are being removed.
     *
     * We also don't need to keep the rest of the lines before the last `shift` as they are
     * embedded in the state machine.
     */
    var c = 0
    var rowsToRemove =
      nonDefDefRowsBeforeSuspensionPoint.values.toList.flatten.map(_.symbol.coord)
    val substituteContinuation = new TreeTypeMap(
      treeMap = {
        case tree if valDefTreeCallsSuspend(tree) && c < suspensionPointsSize - 1 =>
          c += 1
          tpd.EmptyTree
        case tree if valDefTreeCallsSuspend(tree) && c == suspensionPointsSize - 1 =>
          transformSuspendTree(transformedMethod.symbol)
        case tree if treeCallsSuspend(tree) && c < suspensionPointsSize - 1 =>
          c += 1
          tpd.EmptyTree
        case tree if treeCallsSuspend(tree) && c == suspensionPointsSize - 1 =>
          transformSuspendTree(transformedMethod.symbol)
        case tree if c < suspensionPointsSize && rowsToRemove.contains(tree.symbol.coord) =>
          rowsToRemove = rowsToRemove.zipWithIndex.collect {
            case (coord, index) if index != rowsToRemove.indexOf(tree.symbol.coord) =>
              coord
          }
          tpd.EmptyTree
        case tree if globalVars.exists(matchesNameCoord(_, tree)) =>
          ref(globalVars.find(matchesNameCoord(_, tree)).get.symbol)
        case tt
            if tt.symbol.exists &&
              tt.symbol.is(TermParam) &&
              tt.symbol.owner.denot.matches(tree.symbol) &&
              transformedMethodParamsWithoutCompletion.exists(
                _.symbol.denot.matches(tt.symbol)) &&
              transformedMethodParamsAsVals.exists(
                _.symbol.name.show.dropRight(3) == tt.symbol.name.show) =>
          ref(
            transformedMethodParamsAsVals
              .find(_.symbol.name.show.dropRight(3) == tt.symbol.name.show)
              .get
              .symbol)
        case tree =>
          tree
      },
      substFrom = List(parent) ++ oldMethodParamSymbols ++ contextFunctionOwner.toList,
      substTo = List(transformedMethod.symbol) ++ transformedMethodParamSymbols ++
        List(transformedMethod.symbol),
      oldOwners = List(parent) ++ contextFunctionOwner.toList,
      newOwners = List(transformedMethod.symbol, transformedMethod.symbol)
    )

    val transformedMethodBody =
      substituteContinuation.transform(rhs) match
        case Trees.Block(stats, expr) =>
          val allStats = transformedMethodParamsAsVals ++ globalVars ++ stats
          flattenBlock(tpd.Block(allStats, expr))
        case tree => tree

    tpd.Thicket(
      continuationStateMachineClass ::
        cpy.DefDef(transformedMethod)(rhs = transformedMethodBody) ::
        Nil)

  end transformSuspensionsSuspendingStateMachineAux
