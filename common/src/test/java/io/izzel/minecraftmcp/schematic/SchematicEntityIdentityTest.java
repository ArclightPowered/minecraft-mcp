package io.izzel.minecraftmcp.schematic;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchematicEntityIdentityTest {
 @Test void cloneChangesIdentityWithoutMutatingSourceOrDroppingPayload() throws Exception {
  CompoundTag source=new CompoundTag();
  source.putIntArray("UUID",new int[]{1,2,3,4});
  source.putString("CustomName","fixture");
  CompoundTag passenger=new CompoundTag();passenger.putIntArray("UUID",new int[]{5,6,7,8});passenger.putString("id","minecraft:pig");
  ListTag passengers=new ListTag();passengers.add(passenger);source.put("Passengers",passengers);
  var method=ServerSchematicTools.class.getDeclaredMethod("copyForEntityPlacement",CompoundTag.class);method.setAccessible(true);
  CompoundTag clone=(CompoundTag)method.invoke(null,source);
  assertFalse(clone.contains("UUID"));
  assertFalse(clone.getList("Passengers").orElseThrow().getCompound(0).orElseThrow().contains("UUID"));
  assertEquals("fixture",clone.getString("CustomName").orElseThrow());
  assertTrue(source.contains("UUID"));assertTrue(passenger.contains("UUID"));
 }
}
