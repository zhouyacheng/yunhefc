package com.yunhefc.grouping.domain;

import java.util.SplittableRandom;

/**
 * 分配阶段的随机源；生产使用安全种子驱动的 SplittableRandom，测试可固定种子或记录调用顺序。
 */
public interface GroupingRandom {

    boolean nextBoolean();

    double nextDouble();

    static GroupingRandom seeded(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        return new GroupingRandom() {
            @Override
            public boolean nextBoolean() {
                return random.nextBoolean();
            }

            @Override
            public double nextDouble() {
                return random.nextDouble();
            }
        };
    }
}
