package io.izzel.minecraftmcp.condition;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ConditionValidatorTest {
    private static final Set<String> KNOWN = Set.of("client", "world", "screen");

    @Test
    void reportsAllUnknownNamesInOnePass() {
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("sceen.title != null && invntory.count > 0"));
        assertTrue(error.getMessage().contains("sceen"), error.getMessage());
        assertTrue(error.getMessage().contains("invntory"), error.getMessage());
        assertTrue(error.getMessage().contains("properties"), error.getMessage());
    }

    @Test
    void listsKnownNamesIncludingTheBuiltinRoot() {
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("nosuch == 1"));
        assertTrue(error.getMessage().contains("$"), error.getMessage());
        assertTrue(error.getMessage().contains("client"), error.getMessage());
        assertTrue(error.getMessage().contains(" in tests"), error.getMessage());
    }

    @Test
    void dollarPrefixedAccessIsDynamicAndExemptFromValidation() {
        validate("missing($.vehicle)");
        validate("$.anything.deeper == null");
    }

    @Test
    void namesInsideFunctionArgumentsAndShortCircuitedBranchesAreChecked() {
        assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("exists(nosuch)"));
        assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("client.inWorld == true || nosuch.value == 1"));
        assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("!(nosuch)"));
    }

    @Test
    void accessMembersAreDataKeysAndNotChecked() {
        validate("world.nosuchfield.deeper == null");
        validate("size(screen.children) > 0");
    }

    @Test
    void unknownFunctionNamesAreRejectedUpFront() {
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("exsits(client)"));
        assertTrue(error.getMessage().contains("exsits"), error.getMessage());
        assertTrue(error.getMessage().contains("known functions"), error.getMessage());
        assertTrue(error.getMessage().contains("exists"), error.getMessage());
        assertTrue(error.getMessage().contains(" in tests"), error.getMessage());
    }

    @Test
    void wrongArgumentCountsAreRejectedUpFront() {
        var missing = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("contains(client.title)"));
        assertTrue(missing.getMessage().contains("contains expects 2 args but got 1"), missing.getMessage());
        var extra = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("exists(client, world)"));
        assertTrue(extra.getMessage().contains("exists expects 1 args but got 2"), extra.getMessage());
    }

    @Test
    void invalidLiteralRegexIsRejectedUpFront() {
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("matches(client.name, \"[\")"));
        assertTrue(error.getMessage().contains("Invalid matches regex"), error.getMessage());
        assertTrue(error.getMessage().contains("[\""), error.getMessage());
        assertTrue(error.getMessage().contains(" in tests"), error.getMessage());
    }

    @Test
    void regexPrecheckCoversOnlyLiteralArguments() {
        validate("matches(client.name, world.pattern)");
        validate("matches(client.name, 3)");
    }

    @Test
    void propertyAndFunctionProblemsAreReportedTogether() {
        var error = assertThrows(ConditionValidator.ConditionValidationException.class,
                () -> validate("sceen.title != null && exsits(client)"));
        assertTrue(error.getMessage().contains("sceen"), error.getMessage());
        assertTrue(error.getMessage().contains("exsits"), error.getMessage());
    }

    @Test
    void allBuiltinFunctionsValidateAtTheirArity() {
        validate("exists(client) && missing(client) && contains(client, \"a\") && startsWith(client, \"a\")"
                + " && endsWith(client, \"a\") && matches(client, \"a+\") && size(client) > 0");
    }

    private static void validate(String expression) {
        ConditionValidator.validate(ConditionParser.parse(expression), KNOWN, " in tests");
    }
}
