package com.atlassian.braid.switching;

import com.atlassian.braid.java.util.BraidMaps;
import com.atlassian.braid.java.util.BraidObjects;

import java.util.List;
import java.util.Map;

public class SwitchingSchemaSourceYamlUtil {
    private static String DELEGATES = "delegates";

    public static List<String> buildDelegates(Map<String, Object> m) {
        return BraidMaps.get(m, DELEGATES)
                .map(BraidObjects::<List<String>>cast)
                .orElseThrow(() -> new IllegalArgumentException("'" + DELEGATES + "' field is expected for switcher schema source."));
    }
}
