package org.jetbrains.plugins.scala
package caches


import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util._
import com.intellij.psi._
import com.intellij.psi.impl.compiled.ClsFileImpl
import com.intellij.psi.util._
import com.intellij.util.containers.{ContainerUtil, Stack}
import org.jetbrains.plugins.scala.debugger.evaluation.ScalaCodeFragment
import org.jetbrains.plugins.scala.extensions.ConcurrentMapExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScModificationTrackerOwner
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTypeDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScPackageImpl.{DoNotProcessPackageObjectException, isPackageObjectProcessing}
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiManager
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.project.UserDataHolderExt

import scala.annotation.tailrec
import scala.util.control.ControlThrowable

/**
 * User: Alexander Podkhalyuzin
 * Date: 08.06.2009
 */
object CachesUtil {

  /** This value is used by cache analyzer
   *
   * @see [[org.jetbrains.plugins.scala.macroAnnotations.CachedMacroUtil.transformRhsToAnalyzeCaches]]
   */
  lazy val timeToCalculateForAnalyzingCaches: ThreadLocal[Stack[Long]] = new ThreadLocal[Stack[Long]] {
    override def initialValue: Stack[Long] = new Stack[Long]()
  }

  /**
   * Do not delete this type alias, it is used by [[org.jetbrains.plugins.scala.macroAnnotations.CachedWithRecursionGuard]]
    *
    * @see [[CachesUtil.getOrCreateKey]] for more info
   */
  type MappedKey[Data, Result] = Key[CachedValue[ConcurrentMap[Data, Result]]]
  type RefKey[Result] = Key[CachedValue[AtomicReference[Result]]]
  private val keys = ContainerUtil.newConcurrentMap[String, Key[_]]()

  /**
   * IMPORTANT:
   * Cached annotations (CachedWithRecursionGuard, CachedMappedWithRecursionGuard, and CachedInsidePsiElement)
   * rely on this method, even though it shows that it is unused
   *
   * If you change this method in any way, please make sure it's consistent with the annotations
   *
   * Do not use this method directly. You should use annotations instead
   */
  def getOrCreateKey[T](id: String): T = keys.atomicGetOrElseUpdate(id, Key.create[T](id)).asInstanceOf[T]

  //keys for getUserData
  val IMPLICIT_TYPE: Key[ScType] = Key.create("implicit.type")
  val IMPLICIT_FUNCTION: Key[PsiNamedElement] = Key.create("implicit.function")
  val IMPLICIT_RESOLUTION: Key[PsiClass] = Key.create("implicit.resolution")
  val NAMED_PARAM_KEY: Key[java.lang.Boolean] = Key.create("named.key")
  val PACKAGE_OBJECT_KEY: Key[(ScTypeDefinition, java.lang.Long)] = Key.create("package.object.key")
  val PROJECT_HAS_DOTTY_KEY: Key[java.lang.Boolean] = Key.create("project.has.dotty")

  def getDependentItem(element: PsiElement)(dep_item: Object = enclosingModificationOwner(element)): Object = {
    element.getContainingFile match {
      case file: ScalaFile if file.isCompiled =>
        if (!ProjectRootManager.getInstance(element.getProject).getFileIndex.isInContent(file.getVirtualFile)) {
          return dep_item
        }
        var dir = file.getParent
        while (dir != null) {
          if (dir.getName == "scala-library.jar") return ModificationTracker.NEVER_CHANGED
          dir = dir.getParent
        }
        ProjectRootManager.getInstance(element.getProject)
      case _: ClsFileImpl => ProjectRootManager.getInstance(element.getProject)
      case _ => dep_item
    }
  }

  def enclosingModificationOwner(elem: PsiElement): ModificationTracker = {
    @tailrec
    def calc(element: PsiElement): ModificationTracker = {
      Option(PsiTreeUtil.getContextOfType(element, false, classOf[ScModificationTrackerOwner])) match {
        case Some(owner) if owner.isValidModificationTrackerOwner => owner.getModificationTracker
        case Some(owner) => calc(owner.getContext)
        case _ if elem != null => ScalaPsiManager.instance(elem.getProject).modificationTracker
        case _ => ScalaPsiManager.instance(element.getProject).modificationTracker
      }
    }

    calc(elem)
  }

  @tailrec
  def updateModificationCount(elem: PsiElement, incModCountOnTopLevel: Boolean = false): Unit = {
    Option(PsiTreeUtil.getContextOfType(elem, false, classOf[ScModificationTrackerOwner], classOf[ScalaCodeFragment])) match {
      case Some(_: ScalaCodeFragment) => //do not update on changes in dummy file
      case Some(owner: ScModificationTrackerOwner) if owner.isValidModificationTrackerOwner =>
        owner.incModificationCount()
      case Some(owner) => updateModificationCount(owner.getContext)
      case _ if incModCountOnTopLevel => ScalaPsiManager.instance(elem.getProject).incModificationCount()
      case _ =>
    }
  }

  case class ProbablyRecursionException[Data](elem: PsiElement,
                                              data: Data,
                                              key: Key[_],
                                              set: Set[ScFunction]) extends ControlThrowable

  def getOrCreateCachedMap[Dom <: PsiElement, Data, Result](elem: Dom,
                                                            key: MappedKey[Data, Result],
                                                            dependencyItem: () => Object): ConcurrentMap[Data, Result] = {

    getOrCreateCachedHolder[Dom, ConcurrentMap[Data, Result]](elem, key, dependencyItem, () => ContainerUtil.newConcurrentMap())
  }


  def getOrCreateCachedRef[Dom <: PsiElement, Result](elem: Dom,
                                                      key: RefKey[Result],
                                                      dependencyItem: () => Object): AtomicReference[Result] = {

    getOrCreateCachedHolder[Dom, AtomicReference[Result]](elem, key, dependencyItem, () => new AtomicReference[Result]())
  }

  private def getOrCreateCachedHolder[Dom <: PsiElement, Holder](elem: Dom,
                                                                 key: Key[CachedValue[Holder]],
                                                                 dependencyItem: () => Object,
                                                                 emptyHolder: () => Holder): Holder = {
    val cachedValue = elem.getOrUpdateUserData(key, {
      val manager = CachedValuesManager.getManager(elem.getProject)
      val provider = new CachedValueProvider[Holder] {
        def compute(): CachedValueProvider.Result[Holder] =
          new CachedValueProvider.Result(emptyHolder(), dependencyItem())
      }
      manager.createCachedValue(provider, false)
    })
    cachedValue.getValue
  }

  //used in CachedWithRecursionGuard
  def handleRecursiveCall[Data, Result](e: PsiElement, data: Data, key: Key[_], defaultValue: => Result): Result = {
    if (isPackageObjectProcessing)
      throw new DoNotProcessPackageObjectException()

    PsiTreeUtil.getContextOfType(e, true, classOf[ScFunction]) match {
      case null => defaultValue
      case fun if fun.isProbablyRecursive => defaultValue
      case fun =>
        fun.setProbablyRecursive(true)
        throw ProbablyRecursionException(e, data, key, Set(fun))
    }
  }
}
