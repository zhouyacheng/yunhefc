package com.yunhefc.grouping.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 队内赛/训练赛的自动分组算法。
 *
 * <p>分数口径：分组分数就是球员星级快照 {@code finalScore}（见 {@link GroupingScoreCalculator}），
 * 不使用出场率、进球、助攻或任何权重配置。
 *
 * <p>执行顺序固定为四步：
 * <ol>
 *   <li><b>逐位置蛇形</b>：按门将 → 后卫 → 中场 → 前锋依次处理，位置内按
 *       {@code finalScore + 噪声} 降序，以 {@code A,B,B,A} 蛇形分入两队；</li>
 *   <li><b>人数修平</b>：两队人数差超过 1 时，从人多的一队移走最低分外场球员；</li>
 *   <li><b>门将修复</b>：候选池至少 2 名门将时，交换保证每队至少 1 名；</li>
 *   <li><b>局部搜索</b>：以 {@link GroupingObjective} 的字典序为目标做跨队交换，
 *       直到不存在严格更优交换或达到轮数上限。</li>
 * </ol>
 *
 * <p>随机性只来自两处：每个位置的起始队、位置内每名球员的噪声（±0.6）。
 * 不做随机重启；同一种子 + 同一输入必然得到同一结果，便于测试与问题复现。
 *
 * <p>本类是纯领域算法：不写库、不依赖 Spring、不校验权限与比赛状态；
 * 持久化、confirm、名单指纹与事务边界由应用服务负责。
 */
public final class GroupingAlgorithm {

    /** 位置编码：0=门将（1=后卫，2=中场，3=前锋，与 player.position 一致）。 */
    private static final int GOALKEEPER = 0;

    /** 局部搜索最少轮数；实际轮数取 {@code max(MIN_ROUNDS, n²)}，小名单也能充分收敛。 */
    private static final int MIN_ROUNDS = 20;

    /**
     * 位置内排序噪声的总幅度：实际扰动范围 {@code [-NOISE_RANGE/2, +NOISE_RANGE/2]}，即 ±0.6。
     * 目的是让星级接近的球员顺序不固定，避免连续重新生成总是同一结果；
     * 星级仍是主要因素，噪声只在相邻分值之间产生翻转。
     */
    private static final double NOISE_RANGE = 1.2;

    /** 定位"最低分外场球员"：finalScore 升序，并列时 playerId 升序，保证移动结果确定。 */
    private static final Comparator<AssignedPlayer> LOWEST_FINAL = Comparator
            .comparing((AssignedPlayer player) -> player.score().finalScore())
            .thenComparing(player -> player.candidate().playerId());

    private GroupingAlgorithm() {
    }

    /**
     * 把候选球员分成 A/B 两队。
     *
     * <p>先按主位置分批、再统一修平与搜索，而不是一开始就做全局优化：
     * 逐位置蛇形能保证每个位置的两队人数与强弱大致对称，局部搜索只需做小幅修正，
     * 结果比纯随机或纯贪心更稳定、也更符合"同位置对位"的直觉。
     *
     * <p>输入顺序不影响结果：方法入口与出口都会按 playerId 排序；
     * 唯一的变量是 {@code random} 的抽取序列。
     *
     * <p>返回的两队已按 playerId 升序，便于上层做指纹、展示与比较。
     */
    public static GroupingAssignment assign(List<AssignedPlayer> players, GroupingRandom random) {
        List<AssignedPlayer> sorted = sortedById(players);
        List<AssignedPlayer> teamA = new ArrayList<>();
        List<AssignedPlayer> teamB = new ArrayList<>();

        // 阶段 1：逐位置蛇形分配。
        // 位置顺序固定为 0→3，位置之间互不影响，因此每个位置只需在本位置内做均衡。
        for (int position = 0; position <= 3; position++) {
            // 空位置也消耗一次随机数，保证固定种子下抽取顺序稳定。
            boolean startWithTeamA = random.nextBoolean();
            int currentPosition = position;
            List<AssignedPlayer> group = sorted.stream()
                    .filter(player -> player.candidate().position() == currentPosition)
                    .toList();
            if (group.isEmpty()) {
                continue;
            }
            // 每名球员先抽一个 [0,1) 噪声。抽签顺序固定为 playerId 升序，
            // 保证随机序列只依赖候选集合，而不依赖调用方传入的列表顺序。
            Map<Long, Double> noise = new HashMap<>();
            for (AssignedPlayer player : group) {
                noise.put(player.candidate().playerId(), random.nextDouble());
            }
            // 位置内按"星级 + 噪声"降序排列，形成强弱交替的序列，再用蛇形分配到两队。
            List<AssignedPlayer> ordered = new ArrayList<>(group);
            ordered.sort(withinPositionOrder(noise));
            // 蛇形：A,B,B,A 循环。相邻强弱名次交替落入不同队，
            // 使两队在每个位置上的平均星级接近；起始队随机以增加结果变化。
            for (int index = 0; index < ordered.size(); index++) {
                boolean startsSlot = index % 4 == 0 || index % 4 == 3;
                boolean toTeamA = startWithTeamA == startsSlot;
                (toTeamA ? teamA : teamB).add(ordered.get(index));
            }
        }

        // 各位置独立蛇形后，人数可能累积失衡；后续三步负责修正。
        int goalkeeperPool = (int) sorted.stream()
                .filter(player -> player.candidate().position() == GOALKEEPER)
                .count();
        // 阶段 2：人数修平（差 > 1 时才动）。
        repairCount(teamA, teamB);
        // 阶段 3：门将修复（候选池 ≥ 2 门将时每队至少 1 名）。
        repairGoalkeeper(teamA, teamB, goalkeeperPool);
        // 阶段 4：以目标函数为准做跨队交换，进一步压缩总分差与位置差。
        localSearch(teamA, teamB, goalkeeperPool);
        return new GroupingAssignment(sortedById(teamA), sortedById(teamB));
    }

    /**
     * 位置内排序：有效分降序 → 球衣号升序（无号码排最后）→ playerId 升序。
     *
     * <p>噪声只作用于有效分的比较，不写回球员分数；
     * 后两级 tie-break 完全确定，保证同分同噪时结果仍可复现。
     */
    private static Comparator<AssignedPlayer> withinPositionOrder(Map<Long, Double> noise) {
        return (left, right) -> {
            int byScore = Double.compare(
                    effectiveScore(right, noise.get(right.candidate().playerId())),
                    effectiveScore(left, noise.get(left.candidate().playerId())));
            if (byScore != 0) {
                return byScore;
            }
            int byJersey = Comparator.nullsLast(Integer::compare)
                    .compare(left.candidate().jersey(), right.candidate().jersey());
            if (byJersey != 0) {
                return byJersey;
            }
            return Long.compare(left.candidate().playerId(), right.candidate().playerId());
        };
    }

    /**
     * 排序用有效分 = 星级 + 噪声偏移。
     *
     * <p>{@code noise ∈ [0,1)} 经 {@code (noise - 0.5) × 1.2} 映射为 [-0.6, +0.6)；
     * 星级差小于 1.2 的球员可能互换顺序，差距更大时星级起决定作用。
     * 这样既保留"强者优先对位"的主序，又让同档球员的分组不是固定解。
     */
    private static double effectiveScore(AssignedPlayer player, double noise) {
        return player.score().finalScore().doubleValue() + (noise - 0.5) * NOISE_RANGE;
    }

    /**
     * 人数修平：把两队人数差压到 0 或 1。
     *
     * <p>优先移动"最低分外场球员"：门将是稀缺位置，移动门将会破坏刚形成的门将分布，
     * 所以只有该队除门将外无人可移时才降级移动门将。
     * 移动方向始终是"人多 → 人少"，循环直到差 ≤ 1（偶数人差 0，奇数人差 1）。
     */
    static void repairCount(List<AssignedPlayer> teamA, List<AssignedPlayer> teamB) {
        while (Math.abs(teamA.size() - teamB.size()) > 1) {
            List<AssignedPlayer> larger = teamA.size() > teamB.size() ? teamA : teamB;
            List<AssignedPlayer> smaller = larger == teamA ? teamB : teamA;
            AssignedPlayer moved = larger.stream()
                    .filter(player -> player.candidate().position() != GOALKEEPER)
                    .min(LOWEST_FINAL)
                    .orElseGet(() -> larger.stream().min(LOWEST_FINAL).orElse(null));
            if (moved == null) {
                return;
            }
            larger.remove(moved);
            smaller.add(moved);
        }
    }

    /**
     * 门将修复：候选池至少 2 名门将时，保证每队各至少 1 名。
     *
     * <p>只有 0/1 名门将时不做任何调整——门将本来就不够分，硬换只会让另一队也缺门将，
     * 由 {@link GroupingWarnings} 输出提示即可。
     * 修复只做一次：交换后人数不变，且 {@link GroupingObjective} 把"缺门将"列为最高优先级，
     * 后续局部搜索不会再把门将换走。
     */
    static void repairGoalkeeper(List<AssignedPlayer> teamA,
                                 List<AssignedPlayer> teamB,
                                 int goalkeeperPool) {
        if (goalkeeperPool < 2) {
            return;
        }
        long goalkeepersA = countPosition(teamA, GOALKEEPER);
        long goalkeepersB = countPosition(teamB, GOALKEEPER);
        if (goalkeepersA == 0 && goalkeepersB > 0) {
            swapOneGoalkeeper(teamB, teamA);
        } else if (goalkeepersB == 0 && goalkeepersA > 0) {
            swapOneGoalkeeper(teamA, teamB);
        }
    }

    /**
     * 从 donor 取一名门将换给 receiver，换回 receiver 中最低分的外场球员。
     *
     * <p>门将固定选 playerId 最小者、外场固定选最低分者，均为确定性选择：
     * 同样的初始结果每次修复得到同样的交换，避免引入额外随机源。
     */
    private static void swapOneGoalkeeper(List<AssignedPlayer> donor, List<AssignedPlayer> receiver) {
        AssignedPlayer goalkeeper = donor.stream()
                .filter(player -> player.candidate().position() == GOALKEEPER)
                .min(Comparator.comparing(player -> player.candidate().playerId()))
                .orElse(null);
        AssignedPlayer outfield = receiver.stream()
                .filter(player -> player.candidate().position() != GOALKEEPER)
                .min(LOWEST_FINAL)
                .orElse(null);
        if (goalkeeper == null || outfield == null) {
            return;
        }
        donor.remove(goalkeeper);
        receiver.remove(outfield);
        donor.add(outfield);
        receiver.add(goalkeeper);
    }

    /**
     * 局部搜索：反复执行"最优的一次跨队交换"，直到目标函数不再严格变好。
     *
     * <p>采用 best-improvement：每轮遍历全部 A×B 组合，只接受使
     * {@link GroupingObjective} 字典序严格变小的那一次交换。
     * 不接受等值交换，是为了避免在平台期来回互换、无限循环；
     * 轮数上限 {@code max(20, n²)} 只是防御性上限，正常名单会因无改进提前退出。
     *
     * <p>只交换不移动，人数差保持不变；因此前面的人数修平结果不会被破坏。
     * 该过程本身不含随机性，属于确定性收敛，可能停在局部最优——这是设计取舍：
     * 结果稳定可预期，重新生成的差异由初始蛇形阶段的随机性提供。
     */
    static void localSearch(List<AssignedPlayer> teamA,
                            List<AssignedPlayer> teamB,
                            int goalkeeperPool) {
        // 固定顺序后再做下标遍历，使"同等分数时选哪一对交换"完全确定。
        sortById(teamA);
        sortById(teamB);
        int rounds = Math.max(MIN_ROUNDS, (teamA.size() + teamB.size()) * (teamA.size() + teamB.size()));
        GroupingObjective current = GroupingObjective.evaluate(teamA, teamB, goalkeeperPool);
        for (int round = 0; round < rounds; round++) {
            GroupingObjective best = current;
            int bestA = -1;
            int bestB = -1;
            for (int i = 0; i < teamA.size(); i++) {
                for (int j = 0; j < teamB.size(); j++) {
                    // 先试交换、评估后立即还原，只记录当前最优的一对下标。
                    AssignedPlayer fromA = teamA.get(i);
                    AssignedPlayer fromB = teamB.get(j);
                    teamA.set(i, fromB);
                    teamB.set(j, fromA);
                    GroupingObjective candidate = GroupingObjective.evaluate(teamA, teamB, goalkeeperPool);
                    teamA.set(i, fromA);
                    teamB.set(j, fromB);
                    // 严格更优才记录；等值交换会使搜索陷入无意义循环。
                    if (candidate.compareTo(best) < 0) {
                        best = candidate;
                        bestA = i;
                        bestB = j;
                    }
                }
            }
            if (bestA < 0) {
                return;
            }
            AssignedPlayer fromA = teamA.get(bestA);
            teamA.set(bestA, teamB.get(bestB));
            teamB.set(bestB, fromA);
            current = best;
            sortById(teamA);
            sortById(teamB);
        }
    }

    private static long countPosition(List<AssignedPlayer> team, int position) {
        return team.stream()
                .filter(player -> player.candidate().position() == position)
                .count();
    }

    /** 返回按 playerId 升序的新列表；用于算法入口归一化与最终输出。 */
    private static List<AssignedPlayer> sortedById(List<AssignedPlayer> players) {
        return players.stream()
                .sorted(Comparator.comparing(player -> player.candidate().playerId()))
                .toList();
    }

    /** 原地按 playerId 升序排序；局部搜索每轮交换后调用，保持下标遍历顺序稳定。 */
    private static void sortById(List<AssignedPlayer> players) {
        players.sort(Comparator.comparing(player -> player.candidate().playerId()));
    }
}
