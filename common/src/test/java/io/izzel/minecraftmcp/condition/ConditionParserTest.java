package io.izzel.minecraftmcp.condition;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConditionParserTest {
    @Test
    void parsesBooleanOperatorsAndFunctions() {
        assertNotNull(ConditionParser.parse("client.inWorld && client.screen == null"));
        assertNotNull(ConditionParser.parse("$.client.inWorld && $.client.screen == null"));
        assertNotNull(ConditionParser.parse("contains(screen.title, \"Options\") || size(screen.children) > 0"));
        assertNotNull(ConditionParser.parse("!vehicle.isPassenger"));
    }

    @Test
    void parsesBareIdentifierAsPath() {
        assertNotNull(ConditionParser.parse("inWorld"));
    }
}
