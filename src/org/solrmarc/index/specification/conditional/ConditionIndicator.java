package org.solrmarc.index.specification.conditional;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.marc4j.marc.ControlField;
import org.marc4j.marc.DataField;
import org.marc4j.marc.VariableField;
import org.solrmarc.index.indexer.FullConditionalParser;
import org.solrmarc.index.indexer.FullSym;


public class ConditionIndicator extends Condition
{
    int indicatorNum;
    final String value;
    final Pattern valuePattern;
    int op;

    public ConditionIndicator(String indicatorStr, String value, int op)
    {
        this(null, indicatorStr, value, op);
    }

    public ConditionIndicator(String fieldTag, String indicatorStr, String value, int op)
    {
        super(fieldTag);
        this.indicatorNum = (indicatorStr.equals("1")) ? 1 : 2;
        this.value = value;
        this.op = op;
        if (op == FullSym.MATCH)
        {
            Pattern tmp;
            try
            {
                tmp = Pattern.compile(value);
            }
            catch (PatternSyntaxException pse)
            {
                tmp = Pattern.compile("");
                FullConditionalParser.addError("Invalid Regular Expression in Condition: " + value);
                FullConditionalParser.addError(pse.getMessage());
            }
            valuePattern = tmp;
        }
        else
        {
            valuePattern = null;
        }
    }

    @Override
    public boolean matches(final VariableField f)
    {
        if (f instanceof ControlField) return (false);
        final char indVal = (indicatorNum == 1) ? ((DataField) f).getIndicator1() : ((DataField) f).getIndicator2();
        switch (op) {
            case FullSym.EQU:
                return (indVal == value.charAt(0));
            case FullSym.LT:
                return (indVal < value.charAt(0));
            case FullSym.GT:
                return (indVal > value.charAt(0));
            case FullSym.NEQ:
                return (indVal != value.charAt(0));
            case FullSym.MATCH:
                return (valuePattern.matcher("" + indVal).matches());
        }
        return (false);
    }
}
