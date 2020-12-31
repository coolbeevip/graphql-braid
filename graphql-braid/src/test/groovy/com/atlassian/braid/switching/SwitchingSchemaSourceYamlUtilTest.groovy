package com.atlassian.braid.switching

import org.junit.Test
import org.yaml.snakeyaml.Yaml

class SwitchingSchemaSourceYamlUtilTest {
    @Test
    void testBuildDelegates() {
        def yaml =
'''
namespace: foo
delegates:
  - bar
  - baz
'''
        def sourceConfig = (Map<String, Object>) new Yaml().load(yaml)
        def delegates = SwitchingSchemaSourceYamlUtil.buildDelegates(sourceConfig)

        assert delegates[0] == "bar"
        assert delegates[1] == "baz"
    }
}
