import { HighlightStyle, syntaxHighlighting } from '@codemirror/language'
import { tags as t } from '@lezer/highlight'
import type { Extension } from '@codemirror/state'

/**
 * 浅色主题：Navicat 风格 SQL 语法着色。
 */
export const sqlLightHighlight = HighlightStyle.define([
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

/**
 * 夜间主题：提高对比度，避免深蓝/黑蓝在深色底上看不清。
 */
export const sqlDarkHighlight = HighlightStyle.define([
  { tag: t.keyword, color: '#7EB6FF', fontWeight: '700' },
  { tag: t.operatorKeyword, color: '#7EB6FF', fontWeight: '700' },
  { tag: t.modifier, color: '#7EB6FF', fontWeight: '700' },
  { tag: t.controlKeyword, color: '#7EB6FF', fontWeight: '700' },
  { tag: t.definitionKeyword, color: '#7EB6FF', fontWeight: '700' },
  { tag: t.moduleKeyword, color: '#7EB6FF', fontWeight: '700' },
  { tag: t.typeName, color: '#4EC9B0', fontWeight: '600' },
  { tag: t.standard(t.typeName), color: '#4EC9B0', fontWeight: '600' },
  { tag: t.bool, color: '#7EB6FF', fontWeight: '700' },
  { tag: t.null, color: '#7EB6FF', fontWeight: '700' },
  { tag: t.number, color: '#B5CEA8' },
  { tag: t.integer, color: '#B5CEA8' },
  { tag: t.float, color: '#B5CEA8' },
  { tag: t.string, color: '#CE9178' },
  { tag: t.special(t.string), color: '#CE9178' },
  { tag: t.character, color: '#CE9178' },
  { tag: t.comment, color: '#6A9955', fontStyle: 'italic' },
  { tag: t.lineComment, color: '#6A9955', fontStyle: 'italic' },
  { tag: t.blockComment, color: '#6A9955', fontStyle: 'italic' },
  { tag: t.operator, color: '#D4D4D4' },
  { tag: t.compareOperator, color: '#D4D4D4' },
  { tag: t.logicOperator, color: '#7EB6FF', fontWeight: '700' },
  { tag: t.arithmeticOperator, color: '#D4D4D4' },
  { tag: t.punctuation, color: '#D4D4D4' },
  { tag: t.paren, color: '#D4D4D4' },
  { tag: t.bracket, color: '#D4D4D4' },
  { tag: t.squareBracket, color: '#D4D4D4' },
  { tag: t.brace, color: '#D4D4D4' },
  { tag: t.separator, color: '#D4D4D4' },
  { tag: t.name, color: '#9CDCFE' },
  { tag: t.variableName, color: '#9CDCFE' },
  { tag: t.propertyName, color: '#9CDCFE' },
  { tag: t.labelName, color: '#9CDCFE' },
  { tag: t.function(t.variableName), color: '#DCDCAA', fontWeight: '600' },
  { tag: t.special(t.variableName), color: '#9CDCFE' },
  { tag: t.meta, color: '#858585' },
  { tag: t.invalid, color: '#F44747', textDecoration: 'underline' },
])

/** @deprecated 使用 sqlHighlightingFor */
export const sqlNavicatHighlight = sqlLightHighlight
/** @deprecated 使用 sqlHighlightingFor */
export const sqlNavicatHighlighting = syntaxHighlighting(sqlLightHighlight)

/** 按明暗主题返回 SQL 语法高亮扩展。 */
export function sqlHighlightingFor(dark: boolean): Extension {
  return syntaxHighlighting(dark ? sqlDarkHighlight : sqlLightHighlight)
}
