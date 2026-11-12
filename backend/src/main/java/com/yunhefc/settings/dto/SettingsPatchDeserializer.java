package com.yunhefc.settings.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.yunhefc.settings.dto.SettingsDtos.SettingsPatch;

import java.io.IOException;

/**
 * 9 项配置只接受 JSON 整数；小数、数字字符串与 null 之外的标量一律 40001。
 */
public final class SettingsPatchDeserializer extends JsonDeserializer<SettingsPatch> {

    @Override
    public SettingsPatch deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        if (!node.isObject()) {
            throw MismatchedInputException.from(parser, SettingsPatch.class, "settings 必须为对象");
        }
        return new SettingsPatch(
                integer(node, parser, "maxPlayers"),
                integer(node, parser, "maxGuests"),
                integer(node, parser, "maxWaitlist"),
                integer(node, parser, "groupingManualWeight"),
                integer(node, parser, "groupingAutoWeight"),
                integer(node, parser, "groupingAppearanceWeight"),
                integer(node, parser, "groupingGoalWeight"),
                integer(node, parser, "groupingAssistWeight"),
                integer(node, parser, "groupingMinAppearances"));
    }

    private static Integer integer(JsonNode node, JsonParser parser, String field) throws MismatchedInputException {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw MismatchedInputException.from(parser, SettingsPatch.class,
                    "settings." + field + " 必须为整数");
        }
        return value.intValue();
    }
}
