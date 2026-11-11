package com.yunhefc.grouping.domain;

import com.yunhefc.contracts.settings.GroupingSettings;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * 解析后的分组权重；主/子权重非法时按规格整组回退。
 */
public record GroupingWeights(
        BigDecimal manualWeight,
        BigDecimal autoWeight,
        BigDecimal appearanceWeight,
        BigDecimal goalWeight,
        BigDecimal assistWeight,
        int minAppearances,
        BigDecimal manualRatio,
        BigDecimal autoRatio,
        BigDecimal appearanceRatio,
        BigDecimal goalRatio,
        BigDecimal assistRatio) {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal DEFAULT_MANUAL = BigDecimal.valueOf(70);
    private static final BigDecimal DEFAULT_AUTO = BigDecimal.valueOf(30);
    private static final BigDecimal DEFAULT_APPEARANCE = BigDecimal.valueOf(50);
    private static final BigDecimal DEFAULT_GOAL = BigDecimal.valueOf(25);
    private static final BigDecimal DEFAULT_ASSIST = BigDecimal.valueOf(25);
    private static final int DEFAULT_MIN_APPEARANCES = 3;

    public static GroupingWeights resolve(GroupingSettings settings) {
        BigDecimal manual = settings.manualWeight();
        BigDecimal auto = settings.autoWeight();
        if (invalidMain(manual, auto)) {
            manual = DEFAULT_MANUAL;
            auto = DEFAULT_AUTO;
        }

        BigDecimal appearance = settings.appearanceWeight();
        BigDecimal goal = settings.goalWeight();
        BigDecimal assist = settings.assistWeight();
        if (invalidSub(appearance, goal, assist)) {
            appearance = DEFAULT_APPEARANCE;
            goal = DEFAULT_GOAL;
            assist = DEFAULT_ASSIST;
        }

        int minAppearances = settings.minAppearances() < 0
                ? DEFAULT_MIN_APPEARANCES : settings.minAppearances();
        BigDecimal mainTotal = manual.add(auto);
        BigDecimal subTotal = appearance.add(goal).add(assist);
        return new GroupingWeights(
                manual, auto, appearance, goal, assist, minAppearances,
                ratio(manual, mainTotal),
                ratio(auto, mainTotal),
                ratio(appearance, subTotal),
                ratio(goal, subTotal),
                ratio(assist, subTotal));
    }

    private static boolean invalidMain(BigDecimal manual, BigDecimal auto) {
        return manual == null || auto == null
                || manual.signum() < 0 || auto.signum() < 0
                || manual.add(auto).compareTo(HUNDRED) != 0;
    }

    private static boolean invalidSub(BigDecimal appearance, BigDecimal goal, BigDecimal assist) {
        return appearance == null || goal == null || assist == null
                || appearance.signum() < 0 || goal.signum() < 0 || assist.signum() < 0
                || appearance.add(goal).add(assist).signum() <= 0;
    }

    private static BigDecimal ratio(BigDecimal value, BigDecimal total) {
        return value.divide(total, new MathContext(10, RoundingMode.HALF_UP));
    }
}
