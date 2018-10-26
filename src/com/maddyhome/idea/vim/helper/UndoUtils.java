package com.maddyhome.idea.vim.helper;

import com.intellij.openapi.diagnostic.Logger;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * @author linxixin@cvte.com
 * @since 1.0
 */
public class UndoUtils {

    private static final Logger LOG = Logger.getInstance(UndoUtils.class);

    public static Object invokeMethod(Class cls, String method, List<Class> paramCls, Object targetObj, Object... params) {

        try {
            Method clsDeclaredMethod = cls.getDeclaredMethod(method, paramCls.toArray(new Class[paramCls.size()]));
            clsDeclaredMethod.setAccessible(true);
            Object getUndoStacksHolder = clsDeclaredMethod.invoke(targetObj, params);
            return getUndoStacksHolder;
        } catch (InvocationTargetException e){
            return null;
        }catch (Exception e) {
            LOG.error(e);
            throw new RuntimeException(e);
        }
    }
}
