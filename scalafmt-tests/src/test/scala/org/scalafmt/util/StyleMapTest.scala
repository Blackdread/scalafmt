package org.scalafmt.util

import scala.meta._

import org.scalafmt.config.BinPack
import org.scalafmt.config.Newlines
import org.scalafmt.config.ScalafmtConfig
import org.scalafmt.internal.FormatOps
import munit.FunSuite

class StyleMapTest extends FunSuite {
  test("basic") {
    val code =
      """object a {
        |  // scalafmt: { maxColumn = 100 }
        |  println(1)
        |  // scalafmt: { maxColumn = 110 }
        |}
      """.stripMargin.parse[Source].get
    val m = new FormatOps(code, ScalafmtConfig.default)
    assert(
      m.styleMap
        .at(m.tokens.head)
        .maxColumn == ScalafmtConfig.default.maxColumn
    )
    assert(
      m.styleMap
        .at(m.tokens.find(_.left.syntax == "println").get)
        .maxColumn == 100
    )
    assert(m.styleMap.at(m.tokens.last).maxColumn == 110)
  }

  test("align.tokens.add") {
    val code =
      """object a {
        |  // scalafmt: { align.tokens.add = [{ code="=", owner=".*" }] }
        |  println(1)
        |}
      """.stripMargin.parse[Source].get
    val formatOps = new FormatOps(code, ScalafmtConfig.defaultWithAlign)
    val headToken = formatOps.tokens.head
    val printlnToken = formatOps.tokens.find(_.left.syntax == "println").get

    val defaultEquals = "(Enumerator.Val|Defn.(Va(l|r)|GivenAlias|Def|Type))"
    val overrideEquals = ".*"

    val headConfig = formatOps.styleMap.at(headToken)
    val printlnConfig = formatOps.styleMap.at(printlnToken)
    val newFormatOps = new FormatOps(code, printlnConfig)

    val newHeadConfig = newFormatOps.styleMap.at(headToken)
    assert(headConfig.alignMap("=").pattern() == defaultEquals)
    assert(newHeadConfig.alignMap("=").pattern() == overrideEquals)

    val newPrintlnConfig = newFormatOps.styleMap.at(printlnToken)
    assert(printlnConfig.alignMap("=").pattern() == overrideEquals)
    assert(newPrintlnConfig.alignMap("=").pattern() == overrideEquals)
  }

  test("newlines.implicitParamListModifierForce") {
    val code =
      """object a {
        |  // scalafmt: { newlines.implicitParamListModifierForce = [after] }
        |  println(1)
        |}
      """.stripMargin.parse[Source].get
    val formatOps = new FormatOps(
      code,
      ScalafmtConfig(newlines =
        Newlines(implicitParamListModifierForce = List(Newlines.before))
      )
    )
    val token1 = formatOps.tokens.head
    val token2 = formatOps.tokens.find(_.left.syntax == "println").get

    val defaultValue = List(Newlines.before)
    val overrideValue = List(Newlines.after)

    val style1 = formatOps.styleMap.at(token1)
    val style2 = formatOps.styleMap.at(token2)
    val formatOps2 = new FormatOps(code, style2)

    val newStyle1 = formatOps2.styleMap.at(token1)
    assert(style1.newlines.implicitParamListModifierForce == defaultValue)
    assert(newStyle1.newlines.implicitParamListModifierForce == overrideValue)

    val newStyle2 = formatOps2.styleMap.at(token2)
    assert(style2.newlines.implicitParamListModifierForce == overrideValue)
    assert(newStyle2.newlines.implicitParamListModifierForce == overrideValue)
  }

  test("StyleMap.numEntries: unsafeCallSite for literalArgumentLists") {
    /* By default, binPack.literalsMinArgCount=5, binPack.literalArgumentLists=true.
     *
     * Therefore, every println below (with 6 literal arguments) results in an
     * attempt to set binPack.unsafeCallSite=true on the opening parenthesis,
     * and then to reset it back to false on the closing parenthesis.
     *
     * One test uses the default settings (meaning, unsafeCallSite=false) while
     * the other starts with BinPack.enabled, which includes unsafeCallSite=true.
     */
    val code =
      """object a {
        |  println(1, 2, 3, 4, 5, 6)
        |  println(1, 2, 3, 4, 5, 6)
        |  // scalafmt: { binPack.unsafeCallSite = false }
        |  println(1, 2, 3, 4, 5, 6)
        |}
      """.stripMargin.parse[Source].get
    val fops1 = new FormatOps(code, ScalafmtConfig.default)
    val fops2 = new FormatOps(code, ScalafmtConfig(binPack = BinPack.enabled))
    /*
     * - 1: initial style
     * - 6: all 3 pairs of "()"
     * - "// scalafmt:" override does nothing, since the value doesn't change
     */
    assertEquals(fops1.styleMap.numEntries, 7)
    /*
     * - 1: initial style
     * - 1: "// scalafmt:" override
     * - 2: last pair of "()"
     * - the first two pairs of () do nothing, since the value doesn't change
     */
    assertEquals(fops2.styleMap.numEntries, 4)
  }

}
