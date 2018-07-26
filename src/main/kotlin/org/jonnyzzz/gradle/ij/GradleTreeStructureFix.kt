package org.jonnyzzz.gradle.ij

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.TreeStructureProvider
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.nodes.PsiDirectoryNode
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.components.ProjectComponent
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import kotlin.coroutines.experimental.buildSequence


val X = Any()

class GradleTreeStructureFix(private val project: Project) : TreeStructureProvider, ProjectComponent {
  override fun modify(parent: AbstractTreeNode<*>,
                      children: MutableCollection<AbstractTreeNode<*>>,
                      settings: ViewSettings?): MutableCollection<AbstractTreeNode<*>> {

    parent as? PsiDirectoryNode ?: return children

    //The class is not visible outside
    if (parent.javaClass.name != "org.jetbrains.plugins.gradle.projectView.GradleTreeStructureProvider\$GradleModuleDirectoryNode") {
      return children
    }

    val module = catchAll { parent.javaClass.getDeclaredField("myModule")?.get(parent) as? Module }

    synchronized(X) {
      println("Root: " + parent.name + " [ $module ] ")
      children.forEach {
        when (it) {
          is PsiDirectoryNode -> println("   - :" + it.value.name)
          is PsiFileNode -> println("   - :" + it.value.name)
          else -> println("   - :" + it.javaClass.name)
        }
      }
      println()
    }

    fun childrenToMerge(node: AbstractTreeNode<*>) = buildSequence {
      fun AbstractTreeNode<*>.nextSingleChild() = this.children.singleOrNull() as? PsiDirectoryNode

      var child = node.nextSingleChild()

      while (child != null) {
        yield(child)
        child = child.nextSingleChild()
      }

    }.filterNotNull().toList()


    return children.map {

      val childrenToMerge = childrenToMerge(it)
      if (children.isNotEmpty()) {
        MergedProjectViewNode(
                project,
                MergedProjectViewValue(
                        parent,
                        childrenToMerge,
                        childrenToMerge.last().children.filterNotNull().filterIsInstance<ProjectViewNode<*>>()
                ),
                parent.settings)
      } else {
        it
      }
    }.toMutableList()
  }
}

inline fun <T> catchAll(x: () -> T): T? = try {
  x()
} catch (t: Throwable) {
  null
}

data class MergedProjectViewValue(
        val parent: PsiDirectoryNode,
        val mergedChildren: List<PsiDirectoryNode>,
        val newChildren : List<ProjectViewNode<*>>
)

class MergedProjectViewNode(project: Project,
                            val value: MergedProjectViewValue,
                            settings: ViewSettings
) : ProjectViewNode<MergedProjectViewValue>(project, value, settings) {
  override fun contains(p0: VirtualFile): Boolean {
    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
  }

  override fun update(data: PresentationData) {
    for (c in value.mergedChildren) {
      data.addText(c.value.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      data.addText(" / ", SimpleTextAttributes.LINK_BOLD_ATTRIBUTES)
    }
  }

  override fun getChildren() = value.newChildren.toMutableList()
}