package de.robv.android.xposed;

public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
    public static final class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        boolean returnEarly;
        public Object getResult() { return result; }
        public void setResult(Object value) { result = value; throwable = null; returnEarly = true; }
        public boolean hasThrowable() { return throwable != null; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable value) { throwable = value; result = null; returnEarly = true; }
    }
}
