package io.izzel.minecraftmcp.command;

import net.minecraft.commands.CommandResultCallback;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CapturingCommandSource implements CommandSource {
    private final List<String> output = new ArrayList<>();
    private boolean success;
    private int resultValue;
    private boolean completed;

    @Override
    public void sendSystemMessage(Component component) {
        output.add(component.getString());
    }

    @Override
    public boolean acceptsSuccess() {
        return true;
    }

    @Override
    public boolean acceptsFailure() {
        return true;
    }

    @Override
    public boolean shouldInformAdmins() {
        return false;
    }

    public CommandResultCallback callback() {
        return (succeeded, result) -> {
            this.success = succeeded;
            this.resultValue = result;
            this.completed = true;
        };
    }

    public Map<String, Object> toMap(String command) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", completed ? (success ? "succeeded" : "failed") : "dispatched");
        map.put("command", command);
        map.put("success", success);
        map.put("resultValue", resultValue);
        map.put("completed", completed);
        map.put("output", List.copyOf(output));
        return map;
    }
}
