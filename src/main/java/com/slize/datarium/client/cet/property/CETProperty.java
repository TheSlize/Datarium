package com.slize.datarium.client.cet.property;

import com.slize.datarium.client.cet.CETLru;
import com.slize.datarium.client.cet.CETSubject;

import java.util.Properties;

public abstract class CETProperty {

    protected final CETLru.Booleans initialResults = new CETLru.Booleans();
    private boolean canUpdate = true;

    public static String read(Properties properties, int ruleNumber, String... ids) throws Invalid {
        for (String id : ids) {
            String key = id + "." + ruleNumber;
            if (properties.containsKey(key)) {
                String value = properties.getProperty(key).trim();
                if (!value.isEmpty()) return value;
            }
        }
        throw new Invalid("failed to read property [" + ids[0] + "]");
    }

    public boolean test(CETSubject subject, boolean isUpdate) {
        if (isUpdate && !canUpdate && initialResults.containsKey(subject.uuid())) {
            return initialResults.getBoolean(subject.uuid());
        }
        try {
            return testInternal(subject);
        } catch (Exception e) {
            return false;
        }
    }

    protected abstract boolean testInternal(CETSubject subject);

    public final boolean canPropertyUpdate() {
        return canUpdate;
    }

    public void setCanUpdate(boolean canUpdate) {
        this.canUpdate = canUpdate;
    }

    public abstract String[] getPropertyIds();

    public String getPropertyId() {
        String[] ids = getPropertyIds();
        return ids == null || ids.length == 0 ? getClass().getSimpleName() : ids[0];
    }

    public void cacheInitialResult(CETSubject subject) {
        boolean result;
        try {
            result = testInternal(subject);
        } catch (Exception e) {
            result = false;
        }
        initialResults.put(subject.uuid(), result);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[" + getPropertyId() + "]";
    }

    public static final class Invalid extends Exception {
        public Invalid(String reason) {
            super("[CET] " + reason);
        }
    }
}
