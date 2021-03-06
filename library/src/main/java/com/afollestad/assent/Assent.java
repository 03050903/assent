package com.afollestad.assent;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Aidan Follestad (afollestad)
 */
public class Assent extends AssentBase {

    private static Assent mAssent;
    private Activity mActivity;
    private Fragment mFragment;
    private final HashMap<String, CallbackStack> mRequestQueue;

    private Assent() {
        mRequestQueue = new HashMap<>();
    }

    private static Assent instance() {
        // Singleton reduces static variables and prevents pre-mature garbage collection
        if (mAssent == null)
            mAssent = new Assent();
        return mAssent;
    }

    public static void setFragment(@Nullable Fragment from, @Nullable Fragment context) {
        if (context == null) {
            final Fragment current = instance().mFragment;
            if (current != null && from != null && from.getClass().getName().equals(current.getClass().getName())) {
                instance().mFragment = null;
//                LOG("Fragment set to (null)");
            }
        } else {
            instance().mFragment = context;
//            LOG("Fragment set to %s", context.getClass().getSimpleName());
        }
    }

    public static void setActivity(@NonNull Activity from, @Nullable Activity context) {
        if (context == null) {
            final Activity current = instance().mActivity;
            if (current != null && from.getClass().getName().equals(current.getClass().getName())) {
                instance().mActivity = null;
                instance().mFragment = null;
//                LOG("Activity set to (null)");
            }
        } else {
            instance().mActivity = context;
//            LOG("Activity set to %s", context.getClass().getSimpleName());
        }
    }

    private static void invalidateContext() {
        if ((instance().mActivity == null || instance().mActivity.isFinishing()) &&
                (instance().mFragment == null || instance().mFragment.getActivity() == null)) {
            throw new IllegalStateException("You must set an Activity or Fragment to Assent.");
        }
    }

    private static HashMap<String, CallbackStack> requestQueue() {
        return instance().mRequestQueue;
    }

    public static void handleResult(@NonNull String[] permissions, @NonNull int[] grantResults) {
        LOG("Handling result for permissions: %s, results: %s", join(permissions), join(grantResults));
        synchronized (requestQueue()) {
            final String cacheKey = getCacheKey(permissions);
            final CallbackStack callbackStack = requestQueue().get(cacheKey);
            if (callbackStack == null) {
                LOG("No callback stack found for key %s, there are %d total callback stacks.", cacheKey, requestQueue().size());
                return;
            }
            final PermissionResultSet result = PermissionResultSet.create(permissions, grantResults);
            callbackStack.sendResult(result);
            requestQueue().remove(cacheKey);
            LOG("Result for %s handled to %d callbacks.", cacheKey, callbackStack.size());

            if (requestQueue().size() > 0) {
                for (Map.Entry<String, CallbackStack> entry : requestQueue().entrySet()) {
                    if (entry.getValue().isExecuted()) {
                        LOG("Callback stack %s was already executed, skipping.", entry.getKey());
                        continue;
                    }
                    LOG("Executing callback stack %s...", entry.getKey());
                    final Assent ins = instance();
                    if (ins.mFragment != null && ins.mFragment.getActivity() != null)
                        entry.getValue().execute(ins.mFragment);
                    else entry.getValue().execute(ins.mActivity);
                }
            }
        }
    }

    public static boolean isPermissionGranted(@NonNull String permission) {
        invalidateContext();
        final Context context = instance().mFragment != null && instance().mFragment.getActivity() != null ?
                instance().mFragment.getActivity() : instance().mActivity;
        return context != null && ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean arraysEqual(@Nullable String[] left, @Nullable String[] right) {
        if (left == null || right == null) {
            return (left == null) == (right == null);
        } else if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!left[i].equals(right[i]))
                return false;
        }
        return true;
    }

    public static void requestPermissions(final @NonNull Object target,
                                          @IntRange(from = 1, to = 99) int requestCode,
                                          @NonNull String... permissions) {
        invalidateContext();
        LOG("Requesting permissions %s with target %s", join(permissions), target.getClass().getName());
        final Method[] methods = target.getClass().getDeclaredMethods();
        Method annotatedMethod = null;
        for (Method m : methods) {
            AfterPermissionResult annotation = m.getAnnotation(AfterPermissionResult.class);
            if (annotation == null) continue;
            else if (!arraysEqual(permissions, annotation.permissions())) continue;
            annotatedMethod = m;
        }
        if (annotatedMethod == null)
            throw new IllegalStateException(String.format("No AfterPermissionResult annotated methods found in %s with a matching permission set.", target.getClass().getName()));
        else if (annotatedMethod.getParameterTypes().length != 1)
            throw new IllegalStateException(String.format("Method %s should only have 1 parameter of type PermissionResultSet.", annotatedMethod.getName()));
        else if (annotatedMethod.getParameterTypes()[0] != PermissionResultSet.class)
            throw new IllegalStateException(String.format("Method %s should only have 1 parameter of type PermissionResultSet.", annotatedMethod.getName()));

        final Method fMethod = annotatedMethod;
        fMethod.setAccessible(true);
        requestPermissions(new AssentCallback() {
            @Override
            public void onPermissionResult(PermissionResultSet result) {
                LOG("Invoking %s for permission result.", fMethod.getName());
                try {
                    fMethod.invoke(target, result);
                } catch (Exception e) {
                    throw new IllegalStateException(String.format("Failed to invoke %s: %s", fMethod.getName(), e.getMessage()), e);
                }
            }
        }, requestCode, permissions);
    }

    public static void requestPermissions(@NonNull AssentCallback callback,
                                          @IntRange(from = 1, to = Integer.MAX_VALUE) int requestCode,
                                          @NonNull String... permissions) {
        invalidateContext();
        LOG("Requesting permissions %s with a callback target.", join(permissions));
        synchronized (requestQueue()) {
            final String cacheKey = getCacheKey(permissions);
            CallbackStack callbackStack = requestQueue().get(cacheKey);
            if (callbackStack != null) {
                callbackStack.setRequestCode(requestCode);
                callbackStack.push(callback);
                LOG("Pushed callback to EXISTING stack %s... stack size: %d", cacheKey, callbackStack.size());
            } else {
                callbackStack = new CallbackStack(requestCode, permissions);
                callbackStack.push(callback);
                final boolean startNow = requestQueue().size() == 0;
                requestQueue().put(cacheKey, callbackStack);
                LOG("Added NEW callback stack %s", cacheKey);
                if (startNow) {
                    LOG("Executing new permission stack now.");
                    if (instance().mFragment != null && instance().mFragment.getActivity() != null)
                        callbackStack.execute(instance().mFragment);
                    else callbackStack.execute(instance().mActivity);
                } else {
                    LOG("New permission stack will be executed later.");
                }
            }
        }
    }
}