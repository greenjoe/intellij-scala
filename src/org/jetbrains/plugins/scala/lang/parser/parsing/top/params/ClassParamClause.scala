package org.jetbrains.plugins.scala.lang.parser.parsing.top.params

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IChameleonElementType
import com.intellij.psi.tree.TokenSet

import org.jetbrains.plugins.scala.util.DebugPrint
import org.jetbrains.plugins.scala.lang.lexer.ScalaTokenTypes
import org.jetbrains.plugins.scala.lang.parser.ScalaElementTypes
import org.jetbrains.plugins.scala.lang.lexer.ScalaElementType
import org.jetbrains.plugins.scala.lang.parser.parsing.types.Type
import org.jetbrains.plugins.scala.lang.parser.util.ParserUtils
import org.jetbrains.plugins.scala.lang.parser.parsing.types.SimpleType
import org.jetbrains.plugins.scala.lang.parser.bnf.BNF
import org.jetbrains.plugins.scala.lang.parser.parsing.top.template.TemplateBody
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.lang.parser.parsing.nl.LineTerminator

/** 
* Created by IntelliJ IDEA.
* User: Alexander.Podkhalyuz
* Date: 08.02.2008
* Time: 11:20:22
* To change this template use File | Settings | File Templates.
*/

/*
 * ClassParamClause ::= [nl] '(' [ClassParam {',' ClassParam}] ')'
 */

object ClassParamClause {
  def parse(builder: PsiBuilder): Boolean = {
    val classParamMarker = builder.mark
    //try to miss nl token
    builder.getTokenType match {
      case ScalaTokenTypes.tLINE_TERMINATOR => {
        //if we find more than one nl => false
        if (!LineTerminator(builder.getTokenText)) {
          classParamMarker.rollbackTo
          return false
        }
        else {
          builder.advanceLexer // Ate nl token
        }
      }
      case _ => {/*so let's parse*/}
    }
    //Look for '('
    builder.getTokenType match {
      case ScalaTokenTypes.tLPARENTHESIS => {
        builder.advanceLexer //Ate '('
        //ok, let's parse parameters
        if (ClassParam parse builder) {
          while (builder.getTokenType == ScalaTokenTypes.tCOMMA) {
            builder.advanceLexer //Ate ,
            if (!(ClassParam parse builder)) {
              classParamMarker.rollbackTo
              return false
            }
          }
        }
      }
      case _ => {
        classParamMarker.rollbackTo
        return false
      }
    }
    //Look for ')'
    builder.getTokenType match {
      case ScalaTokenTypes.tRPARENTHESIS => {
        builder.advanceLexer //Ate )
        classParamMarker.done(ScalaElementTypes.CLASS_PARAM_CLAUSE)
        return true
      }
      case _ => {
        classParamMarker.rollbackTo
        return false
      }
    }
  }
}