package com.slize.datarium.client.cem;

import com.slize.datarium.DatariumMain;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class CEMPartMapping {

    // Maps CEM part name -> list of possible field names (deobf and SRG)
    private static final Map<String, String[]> CEM_TO_FIELD_NAMES = new HashMap<>();

    static {
        // Villager parts
        CEM_TO_FIELD_NAMES.put("head", new String[]{"villagerHead", "field_78187_b", "bipedHead", "field_78116_c", "head", "field_78150_a", "field_78132_a", "field_78138_a"});
        CEM_TO_FIELD_NAMES.put("headwear", new String[]{"bipedHeadwear", "field_78114_d"});
        CEM_TO_FIELD_NAMES.put("body", new String[]{"villagerBody", "field_78188_c", "bipedBody", "field_78115_e", "body", "field_78148_b", "field_78130_b", "field_78136_b"});
        CEM_TO_FIELD_NAMES.put("arms", new String[]{"villagerArms", "field_78185_d"});
        CEM_TO_FIELD_NAMES.put("right_leg", new String[]{"rightVillagerLeg", "field_78186_e", "bipedRightLeg", "field_78123_h", "rightLeg", "field_78137_c"});
        CEM_TO_FIELD_NAMES.put("left_leg", new String[]{"leftVillagerLeg", "field_78184_f", "bipedLeftLeg", "field_78124_i", "leftLeg", "field_78134_d"});
        CEM_TO_FIELD_NAMES.put("nose", new String[]{"villagerNose", "field_78189_a"});

        // Biped parts (zombie, skeleton, etc.)
        CEM_TO_FIELD_NAMES.put("right_arm", new String[]{"bipedRightArm", "field_78112_f"});
        CEM_TO_FIELD_NAMES.put("left_arm", new String[]{"bipedLeftArm", "field_78113_g"});

        // Quadruped parts (pig, cow, sheep)
        CEM_TO_FIELD_NAMES.put("right_hind_leg", new String[]{"leg1", "field_78149_c"});
        CEM_TO_FIELD_NAMES.put("left_hind_leg", new String[]{"leg2", "field_78146_d"});
        CEM_TO_FIELD_NAMES.put("right_front_leg", new String[]{"leg3", "field_78147_e"});
        CEM_TO_FIELD_NAMES.put("left_front_leg", new String[]{"leg4", "field_78144_f"});

        // Alternative quadruped naming (used by some CEM files)
        CEM_TO_FIELD_NAMES.put("leg1", new String[]{"leg1", "field_78149_c"});
        CEM_TO_FIELD_NAMES.put("leg2", new String[]{"leg2", "field_78146_d"});
        CEM_TO_FIELD_NAMES.put("leg3", new String[]{"leg3", "field_78147_e"});
        CEM_TO_FIELD_NAMES.put("leg4", new String[]{"leg4", "field_78144_f"});

        // Chicken parts
        CEM_TO_FIELD_NAMES.put("right_wing", new String[]{"rightWing", "field_78135_e"});
        CEM_TO_FIELD_NAMES.put("left_wing", new String[]{"leftWing", "field_78133_f"});
        CEM_TO_FIELD_NAMES.put("bill", new String[]{"bill", "field_78140_g"});
        CEM_TO_FIELD_NAMES.put("chin", new String[]{"chin", "field_78139_h"});

        // Creeper parts
        CEM_TO_FIELD_NAMES.put("creeperArmor", new String[]{"creeperArmor", "field_78127_g"});
    }

    public static Map<String, ModelRenderer> mapParts(ModelBase model, String entityModelName) {
        Map<String, ModelRenderer> result = new HashMap<>();

        if (model == null) {
            return result;
        }

        // Collect all ModelRenderer fields from the model and its superclasses
        Map<String, ModelRenderer> allFields = new HashMap<>();
        Class<?> current = model.getClass();

        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!ModelRenderer.class.isAssignableFrom(field.getType())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    ModelRenderer renderer = (ModelRenderer) field.get(model);
                    if (renderer != null) {
                        allFields.put(field.getName(), renderer);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
            current = current.getSuperclass();
        }

        // Now map CEM part names to model fields
        for (Map.Entry<String, String[]> entry : CEM_TO_FIELD_NAMES.entrySet()) {
            String cemPartName = entry.getKey();
            String[] possibleFieldNames = entry.getValue();

            for (String fieldName : possibleFieldNames) {
                ModelRenderer renderer = allFields.get(fieldName);
                if (renderer != null && !result.containsKey(cemPartName)) {
                    result.put(cemPartName, renderer);
                    DatariumMain.LOGGER.debug("[CEM] Mapped CEM part '{}' -> field '{}'", cemPartName, fieldName);
                    break;
                }
            }
        }

        DatariumMain.LOGGER.info("[CEM] Mapped {} parts for model {}", result.size(), model.getClass().getSimpleName());

        return result;
    }
}