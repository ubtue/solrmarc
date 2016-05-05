package playground.solrmarc.index.extractor.impl.script;

import bsh.BshMethod;
import bsh.Interpreter;
import playground.solrmarc.index.extractor.methodcall.AbstractExtractorMethodCall;

public class ScriptSingleValueMethodCall extends AbstractExtractorMethodCall<String>
{
    private final Interpreter interpreter;
    private final BshMethod method;

    public ScriptSingleValueMethodCall(final Interpreter interpreter, final BshMethod method,
            final String scriptFileName)
    {
        super(scriptFileName, method.getName());
        this.interpreter = interpreter;
        this.method = method;

        if (this.interpreter == null)
        {
            throw new NullPointerException("Interpreter is null.");
        }
        else if (!String.class.isAssignableFrom(this.method.getReturnType()))
        {
            throw new IllegalArgumentException("The method's return type has to be assignable to String:\nScript:  "
                    + scriptFileName + "\nMethod: " + method.toString());
        }
    }

    @Override
    public String invoke(final Object[] parameters) throws Exception
    {
       Object result = method.invoke(parameters, interpreter);
       if (result instanceof String)  
           return((String)result);
       else 
           return(null);
    }
}
