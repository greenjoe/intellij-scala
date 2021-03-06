package org.jetbrains.plugins.scala.editor.importOptimizer

import junit.framework.TestCase
import org.jetbrains.plugins.scala.editor.importOptimizer.ScalaImportOptimizer.ImportTextCreator
import org.junit.Assert

class ImportTextCreatorTest extends TestCase {
  private val lexOrdering = Some(Ordering.String)
  private val scalastyleOrdering = Some(ScalastyleSettings.nameOrdering)
  private val textCreator = new ImportTextCreator()
  import textCreator.getImportText

  def testGetImportText_Root_And_Wildcard(): Unit = {
    val info = ImportInfo("scala.collection", hasWildcard = true, rootUsed = true)
    Assert.assertEquals("import _root_.scala.collection._", getImportText(info, isUnicodeArrow = false, spacesInImports = false, lexOrdering))
  }

  def testGetImportText_Hidden(): Unit = {
    val info = ImportInfo("scala", hiddenNames = Set("Long"))
    Assert.assertEquals("import scala.{Long => _}", getImportText(info, isUnicodeArrow = false, spacesInImports = false, lexOrdering))
  }

  def testGetImportText_Renames(): Unit = {
    val info = ImportInfo("java.lang", renames = Map("Long" -> "JLong"))
    Assert.assertEquals("import java.lang.{Long => JLong}", getImportText(info, isUnicodeArrow = false, spacesInImports = false, lexOrdering))
  }

  def testGetImportText_UnicodeArrowAndSpaces(): Unit = {
    val info = ImportInfo("java.lang", renames = Map("Long" -> "JLong"))
    Assert.assertEquals("import java.lang.{ Long ⇒ JLong }", getImportText(info, isUnicodeArrow = true, spacesInImports = true, lexOrdering))
  }

  def testGetImportText_SortSingles(): Unit = {
    val info = ImportInfo("java.lang", singleNames = Set("Long", "Integer", "Float", "Short"))
    Assert.assertEquals("import java.lang.{Float, Integer, Long, Short}", getImportText(info, isUnicodeArrow = false, spacesInImports = false, lexOrdering))
  }

  def testGetImportText_Renames_Hidden_Singles_Wildcard_Spaces(): Unit = {
    val info = ImportInfo("java.lang",
      singleNames = Set("Integer", "Character", "Runtime"),
      renames = Map("Long" -> "JLong", "Float" -> "JFloat"),
      hiddenNames = Set("System"),
      hasWildcard = true)
    Assert.assertEquals("import java.lang.{ Character, Integer, Runtime, Float => JFloat, Long => JLong, System => _, _ }",
      getImportText(info, isUnicodeArrow = false, spacesInImports = true, lexOrdering))
  }

  def testGetImportText_No_Sorting(): Unit = {
    val info = ImportInfo("java.lang", singleNames = Set("Long", "Integer", "Float", "Short"))
    Assert.assertEquals("import java.lang.{Long, Integer, Float, Short}", getImportText(info, isUnicodeArrow = false, spacesInImports = false, nameOrdering = None))
  }

  def testLexSorting(): Unit = {
    val info = ImportInfo("java.io", singleNames = Set("InputStream", "IOException", "SequenceInputStream"))
    Assert.assertEquals("import java.io.{IOException, InputStream, SequenceInputStream}",
      getImportText(info, isUnicodeArrow = false, spacesInImports = false, lexOrdering))
  }

  def testScalastyleSorting(): Unit = {
    val info = ImportInfo("java.io", singleNames = Set("InputStream", "IOException", "SequenceInputStream"))
    Assert.assertEquals("import java.io.{InputStream, IOException, SequenceInputStream}",
      getImportText(info, isUnicodeArrow = false, spacesInImports = false, scalastyleOrdering))
  }
}