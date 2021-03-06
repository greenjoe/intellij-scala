package org.jetbrains.plugins.scala.debugger.renderers

import java.util

import com.intellij.debugger.engine.evaluation.{EvaluateException, EvaluationContextImpl}
import com.intellij.debugger.ui.impl.ThreadsDebuggerTree
import com.intellij.debugger.ui.impl.watch.{DebuggerTree, LocalVariableDescriptorImpl, NodeDescriptorImpl}
import com.intellij.debugger.ui.tree.render.{ArrayRenderer, ChildrenBuilder, DescriptorLabelListener}
import com.intellij.debugger.ui.tree._
import com.intellij.openapi.util.Disposer
import org.jetbrains.plugins.scala.debugger.ScalaDebuggerTestCase

/**
  * Nikolay.Tropin
  * 14-Mar-17
  */
abstract class RendererTestBase extends ScalaDebuggerTestCase {

  protected def renderLabelAndChildren(variableName: String, render: NodeDescriptor => String = _.getLabel): (String, List[String]) = {
    import scala.collection.JavaConversions._

    val frameTree = new ThreadsDebuggerTree(getProject)
    Disposer.register(getTestRootDisposable, frameTree)
    var testVariableChildren: util.List[DebuggerTreeNode] = null

    val testVariable = managed[LocalVariableDescriptorImpl] {
      val context = evaluationContext()
      val testVariable = localVar(frameTree, context, variableName)
      val renderer = testVariable.getRenderer(getDebugProcess)
      testVariable.setRenderer(renderer)
      testVariable.updateRepresentation(context, DescriptorLabelListener.DUMMY_LISTENER)
      val value = testVariable.calcValue(context)
      renderer.buildChildren(value, new ChildrenBuilder {
        def setChildren(children: util.List[DebuggerTreeNode]) {testVariableChildren = children}

        def getDescriptorManager: NodeDescriptorFactory = frameTree.getNodeFactory

        def getNodeManager: NodeManager = frameTree.getNodeFactory

        def setRemaining(remaining: Int) {}

        def initChildrenArrayRenderer(renderer: ArrayRenderer) {}

        def getParentDescriptor: ValueDescriptor = testVariable
      }, context)

      testVariable
    }

    managed{testVariableChildren map (_.getDescriptor) foreach {
      case impl: NodeDescriptorImpl =>
        impl.updateRepresentation(evaluationContext(), DescriptorLabelListener.DUMMY_LISTENER)
      case a => println(a)
    }}

    //<magic>
    evalResult(variableName)
    //</magic>

    managed {
      (render(testVariable), testVariableChildren.map(child => render(child.getDescriptor)).toList)
    }
  }

  protected def localVar(frameTree: DebuggerTree, evaluationContext: EvaluationContextImpl, name: String) = {
    try {
      val frameProxy = evaluationContext.getFrameProxy
      val local = frameTree.getNodeFactory.getLocalVariableDescriptor(null, frameProxy visibleVariableByName name)
      local setContext evaluationContext
      local
    } catch {
      case e: EvaluateException => null
    }
  }
}
