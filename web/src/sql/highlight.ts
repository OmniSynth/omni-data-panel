import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags as t } from '@lezer/highlight'

/**
 * Navicat 风格的 SQL 语法着色：关键字亮蓝加粗，字符串红，注释绿，数字紫。
 */
export const sqlNavicatHighlight = HighlightStyle.define([
  { tag: t.keyword, color: '#0000FF', fontWeight: '700' },
  { tag: t.operatorKeyword, color: '#0000FF', fontWeight: '700' },
  { tag: t.modifier, color: '#0000FF', fontWeight: '700' },
  { tag: t.controlKeyword, color: '#0000FF', fontWeight: '700' },
  { tag: t.definitionKeyword, color: '#0000FF', fontWeight: '700' },
  { tag: t.moduleKeyword, color: '#0000FF', fontWeight: '700' },
  { tag: t.typeName, color: '#2B91AF', fontWeight: '600' },
  { tag: t.standard(t.typeName), color: '#2B91AF', fontWeight: '600' },
  { tag: t.bool, color: '#0000FF', fontWeight: '700' },
  { tag: t.null, color: '#0000FF', fontWeight: '700' },
  { tag: t.number, color: '#800080' },
  { tag: t.integer, color: '#800080' },
  { tag: t.float, color: '#800080' },
  { tag: t.string, color: '#A31515' },
  { tag: t.special(t.string), color: '#A31515' },
  { tag: t.character, color: '#A31515' },
  { tag: t.comment, color: '#008000', fontStyle: 'italic' },
  { tag: t.lineComment, color: '#008000', fontStyle: 'italic' },
  { tag: t.blockComment, color: '#008000', fontStyle: 'italic' },
  { tag: t.operator, color: '#666666' },
  { tag: t.compareOperator, color: '#666666' },
  { tag: t.logicOperator, color: '#0000FF', fontWeight: '700' },
  { tag: t.arithmeticOperator, color: '#666666' },
  { tag: t.punctuation, color: '#393A34' },
  { tag: t.paren, color: '#393A34' },
  { tag: t.bracket, color: '#393A34' },
  { tag: t.squareBracket, color: '#393A34' },
  { tag: t.brace, color: '#393A34' },
  { tag: t.separator, color: '#393A34' },
  { tag: t.name, color: '#001080' },
  { tag: t.variableName, color: '#001080' },
  { tag: t.propertyName, color: '#001080' },
  { tag: t.labelName, color: '#001080' },
  { tag: t.function(t.variableName), color: '#795E26', fontWeight: '600' },
  { tag: t.special(t.variableName), color: '#001080' },
  { tag: t.meta, color: '#808080' },
  { tag: t.invalid, color: '#FF0000', textDecoration: 'underline' },
])

export const sqlNavicatHighlighting = syntaxHighlighting(sqlNavicatHighlight)
