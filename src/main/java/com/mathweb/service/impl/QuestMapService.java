package com.mathweb.service.impl;

import com.mathweb.dto.request.SaveQuestMapRequest;
import com.mathweb.dto.response.QuestMapResponse;
import com.mathweb.dto.response.QuestMapResponse.MapEdge;
import com.mathweb.dto.response.QuestMapResponse.MapNode;
import com.mathweb.entity.*;
import com.mathweb.enums.NodeStatus;
import com.mathweb.exception.BadRequestException;
import com.mathweb.exception.ResourceNotFoundException;
import com.mathweb.repository.*;
import com.mathweb.util.RoleUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class QuestMapService {

    private final QuestRepository questRepository;
    private final ProblemRepository problemRepository;
    private final ProblemLinkRepository linkRepository;
    private final ProblemAttemptRepository attemptRepository;
    private final UserQuestProgressRepository progressRepository;

    @Value("${app.problem-retry-cooldown-minutes:30}")
    private long retryCooldownMinutes;

    public QuestMapService(QuestRepository questRepository,
                           ProblemRepository problemRepository,
                           ProblemLinkRepository linkRepository,
                           ProblemAttemptRepository attemptRepository,
                           UserQuestProgressRepository progressRepository) {
        this.questRepository = questRepository;
        this.problemRepository = problemRepository;
        this.linkRepository = linkRepository;
        this.attemptRepository = attemptRepository;
        this.progressRepository = progressRepository;
    }

    // ===== READ =====

    @Transactional(readOnly = true)
    public QuestMapResponse getMap(Long questId, Long userId) {
        Quest quest = findQuest(questId);
        if (!Boolean.TRUE.equals(quest.getPublished()) && !RoleUtil.isStaff()) {
            throw new ResourceNotFoundException("Quest", questId);
        }

        List<Problem> problems = problemRepository.findByQuestIdOrderByOrderIndexAsc(questId);
        List<ProblemLink> links = linkRepository.findByQuestId(questId);

        Map<Long, ProblemAttempt> attempts = userId == null ? Map.of()
                : attemptRepository.findByUserIdAndQuestId(userId, questId).stream()
                .collect(Collectors.toMap(a -> a.getProblem().getId(), Function.identity()));
        Set<Long> solved = attempts.values().stream()
                .filter(ProblemAttempt::getSolved)
                .map(a -> a.getProblem().getId())
                .collect(Collectors.toSet());

        boolean unlocked = questUnlocked(quest, userId);
        Map<Long, NodeStatus> statuses = computeStatuses(problems, links, solved, unlocked);

        List<MapNode> nodes = problems.stream().map(p -> {
            ProblemAttempt a = attempts.get(p.getId());
            Category cat = p.getCategory();
            Category main = (cat != null && cat.getParent() != null) ? cat.getParent() : cat;
            return MapNode.builder()
                    .problemId(p.getId())
                    .x(p.getPositionX())
                    .y(p.getPositionY())
                    .start(Boolean.TRUE.equals(p.getStartNode()))
                    .nodeIcon(p.getNodeIcon())
                    .orderIndex(p.getOrderIndex())
                    .xpReward(p.getXpReward())
                    .categoryId(cat != null ? cat.getId() : null)
                    .categoryName(cat != null ? cat.getName() : null)
                    .mainCategoryId(main != null ? main.getId() : null)
                    .mainCategoryName(main != null ? main.getName() : null)
                    .status(statuses.get(p.getId()))
                    .attemptsUsed(effectiveAttemptsUsed(a))
                    .maxAttempts(p.getMaxAttempts())
                    .retryAvailableAt(retryAvailableAt(a))
                    .build();
        }).toList();

        List<MapEdge> edges = links.stream()
                .map(l -> new MapEdge(l.getProblemAId(), l.getProblemBId()))
                .toList();

        return QuestMapResponse.builder()
                .questId(quest.getId())
                .title(quest.getTitle())
                .difficultyLevel(quest.getDifficultyLevel())
                .questUnlocked(unlocked)
                .nodes(nodes)
                .edges(edges)
                .build();
    }

    /** problemId -> status for one user (userId may be null = anonymous). */
    @Transactional(readOnly = true)
    public Map<Long, NodeStatus> getStatuses(Long questId, Long userId) {
        Quest quest = findQuest(questId);
        List<Problem> problems = problemRepository.findByQuestIdOrderByOrderIndexAsc(questId);
        List<ProblemLink> links = linkRepository.findByQuestId(questId);
        Set<Long> solved = userId == null ? Set.of()
                : new HashSet<>(attemptRepository.findSolvedProblemIds(userId, questId));
        return computeStatuses(problems, links, solved, questUnlocked(quest, userId));
    }

    // ===== ADMIN WRITE =====

    @Transactional
    public QuestMapResponse saveMap(Long questId, SaveQuestMapRequest request) {
        findQuest(questId);
        Map<Long, Problem> byId = problemRepository.findByQuestIdOrderByOrderIndexAsc(questId)
                .stream().collect(Collectors.toMap(Problem::getId, Function.identity()));

        Set<Long> placed = new HashSet<>();
        Set<Long> starts = new HashSet<>();
        for (SaveQuestMapRequest.Node n : request.getNodes()) {
            if (!byId.containsKey(n.getProblemId())) {
                throw new BadRequestException("Problem " + n.getProblemId() + " is not in this quest");
            }
            if (!placed.add(n.getProblemId())) {
                throw new BadRequestException("Problem " + n.getProblemId() + " appears twice");
            }
            if (Boolean.TRUE.equals(n.getStart())) starts.add(n.getProblemId());
        }
        if (!placed.containsAll(byId.keySet())) {
            Set<Long> missing = new TreeSet<>(byId.keySet());
            missing.removeAll(placed);
            throw new BadRequestException("Every problem must be on the map. Missing: " + missing);
        }

        record Pair(Long a, Long b) {}
        Set<Pair> pairs = new LinkedHashSet<>();
        for (SaveQuestMapRequest.Edge e : request.getEdges()) {
            if (e.getFrom().equals(e.getTo())) {
                throw new BadRequestException("A path cannot connect a problem to itself");
            }
            if (!byId.containsKey(e.getFrom()) || !byId.containsKey(e.getTo())) {
                throw new BadRequestException("Path " + e.getFrom() + "-" + e.getTo()
                        + " uses a problem outside this quest");
            }
            pairs.add(new Pair(Math.min(e.getFrom(), e.getTo()), Math.max(e.getFrom(), e.getTo())));
        }

        Map<Long, Set<Long>> adj = new HashMap<>();
        for (Pair p : pairs) {
            adj.computeIfAbsent(p.a(), k -> new HashSet<>()).add(p.b());
            adj.computeIfAbsent(p.b(), k -> new HashSet<>()).add(p.a());
        }
        assertReachable(byId.keySet(), starts, adj);

        for (SaveQuestMapRequest.Node n : request.getNodes()) {
            Problem p = byId.get(n.getProblemId());
            p.setPositionX(n.getX());
            p.setPositionY(n.getY());
            p.setStartNode(Boolean.TRUE.equals(n.getStart()));
            p.setNodeIcon(n.getNodeIcon());
        }
        problemRepository.saveAll(byId.values());

        linkRepository.deleteByQuestId(questId);
        linkRepository.saveAll(pairs.stream()
                .map(p -> ProblemLink.builder()
                        .questId(questId).problemAId(p.a()).problemBId(p.b()).build())
                .toList());

        return getMap(questId, null);
    }

    /** Called before publishing: every problem must be reachable from a start node. */
    @Transactional(readOnly = true)
    public void assertMapValid(Long questId) {
        List<Problem> problems = problemRepository.findByQuestIdOrderByOrderIndexAsc(questId);
        Set<Long> starts = problems.stream()
                .filter(p -> Boolean.TRUE.equals(p.getStartNode()))
                .map(Problem::getId).collect(Collectors.toSet());
        Map<Long, Set<Long>> adj = buildAdjacency(linkRepository.findByQuestId(questId));
        assertReachable(problems.stream().map(Problem::getId).toList(), starts, adj);
    }

    // ===== COOLDOWN HELPERS =====

    /** Non-null only while the player is locked out after using all attempts. */
    public LocalDateTime retryAvailableAt(ProblemAttempt a) {
        if (a == null || Boolean.TRUE.equals(a.getSolved())
                || a.getAttemptsUsed() < a.getProblem().getMaxAttempts()
                || a.getLastAttemptAt() == null) {
            return null;
        }
        LocalDateTime t = a.getLastAttemptAt().plusMinutes(retryCooldownMinutes);
        return t.isAfter(LocalDateTime.now()) ? t : null;
    }

    /** Attempts shown to the player: reset to 0 once the cooldown has passed. */
    public int effectiveAttemptsUsed(ProblemAttempt a) {
        if (a == null) return 0;
        boolean exhausted = !Boolean.TRUE.equals(a.getSolved())
                && a.getAttemptsUsed() >= a.getProblem().getMaxAttempts();
        return (exhausted && retryAvailableAt(a) == null) ? 0 : a.getAttemptsUsed();
    }

    // ===== PRIVATE =====

    private Quest findQuest(Long id) {
        return questRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Quest", id));
    }

    private boolean questUnlocked(Quest quest, Long userId) {
        List<Long> completed = userId == null ? List.of()
                : progressRepository.findCompletedQuestIdsByUserId(userId);
        return quest.isUnlocked(completed);
    }

    private Map<Long, NodeStatus> computeStatuses(List<Problem> problems, List<ProblemLink> links,
                                                  Set<Long> solved, boolean questUnlocked) {
        Map<Long, Set<Long>> adj = buildAdjacency(links);
        Map<Long, NodeStatus> result = new HashMap<>();
        for (Problem p : problems) {
            NodeStatus status;
            if (solved.contains(p.getId())) {
                status = NodeStatus.SOLVED;
            } else if (!questUnlocked) {
                status = NodeStatus.LOCKED;
            } else if (Boolean.TRUE.equals(p.getStartNode())
                    || adj.getOrDefault(p.getId(), Set.of()).stream().anyMatch(solved::contains)) {
                status = NodeStatus.AVAILABLE;
            } else {
                status = NodeStatus.LOCKED;
            }
            result.put(p.getId(), status);
        }
        return result;
    }

    private Map<Long, Set<Long>> buildAdjacency(List<ProblemLink> links) {
        Map<Long, Set<Long>> adj = new HashMap<>();
        for (ProblemLink l : links) {
            adj.computeIfAbsent(l.getProblemAId(), k -> new HashSet<>()).add(l.getProblemBId());
            adj.computeIfAbsent(l.getProblemBId(), k -> new HashSet<>()).add(l.getProblemAId());
        }
        return adj;
    }

    private void assertReachable(Collection<Long> nodeIds, Collection<Long> startIds,
                                 Map<Long, Set<Long>> adj) {
        if (nodeIds.isEmpty()) throw new BadRequestException("The quest has no problems");
        if (startIds.isEmpty()) throw new BadRequestException("Mark at least one problem as a start node");

        Set<Long> seen = new HashSet<>(startIds);
        Deque<Long> queue = new ArrayDeque<>(startIds);
        while (!queue.isEmpty()) {
            for (Long next : adj.getOrDefault(queue.poll(), Set.of())) {
                if (seen.add(next)) queue.add(next);
            }
        }
        List<Long> unreachable = nodeIds.stream().filter(id -> !seen.contains(id)).sorted().toList();
        if (!unreachable.isEmpty()) {
            throw new BadRequestException("Problems not reachable from a start node: " + unreachable);
        }
    }
}