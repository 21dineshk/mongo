import java.util.*;
import java.util.stream.Collectors;

public class RuleDomainValidator {

    // ====== PUBLIC RESULT ======
    public static class ValidationResult {
        private final List<String> errors = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();

        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
        public boolean isValid() { return errors.isEmpty(); }

        public void addError(String e) { if (e != null && !e.isBlank()) errors.add(e); }
        public void addWarning(String w) { if (w != null && !w.isBlank()) warnings.add(w); }

        public void addAll(ValidationResult other) {
            if (other == null) return;
            this.errors.addAll(other.errors);
            this.warnings.addAll(other.warnings);
        }
    }

    // ====== INTERNAL EVAL ======
    private static class EvalResult {
        Set<String> possibleStores = new HashSet<>();
        ValidationResult messages = new ValidationResult();
        String summary; // human-readable summary of the subtree
    }

    // ====== DOMAIN INDEXES (matches your screenshot) ======
    public static class DomainIndexes {
        // storeId -> division / banner
        Map<String, String> storeToDivision = new HashMap<>();
        Map<String, String> storeToBanner = new HashMap<>();

        // reverse indexes
        Map<String, Set<String>> divisionToStores = new HashMap<>();
        Map<String, Set<String>> bannerToStores = new HashMap<>();

        // optional extras from your code (not strictly required for this validator)
        Map<String, Set<String>> divisionToBanners = new HashMap<>();
        Map<String, Set<String>> bannerToDivisions = new HashMap<>();

        Set<String> allStores = new HashSet<>();
    }

    // ====== CONDITION MODEL (minimal for this validator) ======
    public static class Condition {
        private String operator;              // AND, OR, IN, NOT_IN
        private String property;              // e.g. msgContext.context.user.storeId / ...banner / ...division.number
        private List<String> value;           // for IN / NOT_IN
        private List<Condition> matchers;     // recursive
        private String type;                  // ignored here
        private String modelId;               // ignored here

        public String getOperator() { return operator; }
        public String getProperty() { return property; }
        public List<String> getValue() { return value; }
        public List<Condition> getMatchers() { return matchers; }

        public void setOperator(String operator) { this.operator = operator; }
        public void setProperty(String property) { this.property = property; }
        public void setValue(List<String> value) { this.value = value; }
        public void setMatchers(List<Condition> matchers) { this.matchers = matchers; }
        public void setType(String type) { this.type = type; }
        public void setModelId(String modelId) { this.modelId = modelId; }
    }

    // Property dimension we care about
    private enum Dimension { STORE, DIVISION, BANNER, UNKNOWN }

    // ====== PUBLIC API ======
    public ValidationResult validateRule(Condition root, DomainIndexes idx) {
        EvalResult eval = evaluate(root, idx, "$");
        return eval.messages; // valid iff errors.isEmpty()
    }

    // ====== RECURSIVE EVALUATION ======
    private EvalResult evaluate(Condition c, DomainIndexes idx, String path) {
        EvalResult out = new EvalResult();

        if (c == null) {
            out.messages.addError(path + ": condition is null");
            out.summary = "null-condition";
            return out;
        }

        String op = safeUpper(c.getOperator());
        switch (op) {
            case "AND" -> evaluateAnd(c, idx, path, out);
            case "OR" -> evaluateOr(c, idx, path, out);
            case "IN" -> evaluateLeaf(c, idx, path, out, false);
            case "NOT_IN" -> evaluateLeaf(c, idx, path, out, true);
            default -> {
                // Ignoring other ops per your request, but flagging so you know
                out.messages.addWarning(path + ": operator '" + c.getOperator() + "' ignored by domain validator");
                out.possibleStores = new HashSet<>(idx.allStores); // neutral
                out.summary = "IGNORED(" + c.getOperator() + ")";
            }
        }

        return out;
    }

    private void evaluateAnd(Condition c, DomainIndexes idx, String path, EvalResult out) {
        List<Condition> children = c.getMatchers();
        if (children == null || children.isEmpty()) {
            out.messages.addError(path + ": AND has no matchers");
            out.summary = "AND(empty)";
            return;
        }

        List<EvalResult> childResults = new ArrayList<>();
        Set<String> intersection = null;

        for (int i = 0; i < children.size(); i++) {
            EvalResult child = evaluate(children.get(i), idx, path + ".matchers[" + i + "]");
            childResults.add(child);
            out.messages.addAll(child.messages);

            if (intersection == null) {
                intersection = new HashSet<>(child.possibleStores);
            } else {
                intersection.retainAll(child.possibleStores);
            }
        }

        if (intersection == null) intersection = new HashSet<>();
        out.possibleStores = intersection;
        out.summary = "AND";

        // If AND is unsatisfiable => error + explanation
        if (intersection.isEmpty()) {
            String reason = explainAndConflict(children, childResults, idx, path);
            out.messages.addError(reason != null ? reason : (path + ": AND block is invalid (no possible customer/store can satisfy all conditions)"));
        } else {
            // AND is satisfiable; we can generate useful warnings for redundant list values
            addAndWarnings(children, childResults, intersection, idx, path, out.messages);
        }
    }

    private void evaluateOr(Condition c, DomainIndexes idx, String path, EvalResult out) {
        List<Condition> children = c.getMatchers();
        if (children == null || children.isEmpty()) {
            out.messages.addError(path + ": OR has no matchers");
            out.summary = "OR(empty)";
            return;
        }

        Set<String> union = new HashSet<>();
        boolean anyPotentiallyValid = false;

        for (int i = 0; i < children.size(); i++) {
            EvalResult child = evaluate(children.get(i), idx, path + ".matchers[" + i + "]");
            out.messages.addAll(child.messages);
            union.addAll(child.possibleStores);
            if (!child.possibleStores.isEmpty()) anyPotentiallyValid = true;
        }

        out.possibleStores = union;
        out.summary = "OR";

        // OR invalid only if all branches are impossible
        if (!anyPotentiallyValid) {
            out.messages.addError(path + ": OR block is invalid (all branches are unsatisfiable)");
        }
    }

    private void evaluateLeaf(Condition c, DomainIndexes idx, String path, EvalResult out, boolean negate) {
        Dimension dim = resolveDimension(c.getProperty());
        List<String> values = (c.getValue() == null) ? List.of() : c.getValue();

        String op = negate ? "NOT_IN" : "IN";
        out.summary = leafSummary(dim, op, values);

        if (dim == Dimension.UNKNOWN) {
            out.messages.addWarning(path + ": unsupported property '" + c.getProperty() + "' ignored");
            out.possibleStores = new HashSet<>(idx.allStores); // neutral to avoid false invalids
            return;
        }

        if (values.isEmpty()) {
            out.messages.addError(path + ": " + op + " has empty value list");
            out.possibleStores = Set.of();
            return;
        }

        // Unknown IDs check + map to stores
        Set<String> matchedStores = new HashSet<>();
        List<String> unknownValues = new ArrayList<>();

        for (String v : values) {
            if (v == null) continue;
            Set<String> storesForValue = storesForDimensionValue(dim, v, idx);
            if (storesForValue.isEmpty()) {
                unknownValues.add(v);
            } else {
                matchedStores.addAll(storesForValue);
            }
        }

        // Unknown value handling:
        // - If all values unknown in IN, this condition matches nothing => invalid leaf
        // - If some unknown, warn
        if (!unknownValues.isEmpty()) {
            out.messages.addWarning(path + ": unknown " + dim.name().toLowerCase() + " values " + unknownValues);
        }

        if (!negate) {
            out.possibleStores = matchedStores;
            if (matchedStores.isEmpty()) {
                out.messages.addError(path + ": IN condition matches no stores");
            }
        } else {
            out.possibleStores = new HashSet<>(idx.allStores);
            out.possibleStores.removeAll(matchedStores);

            if (out.possibleStores.isEmpty()) {
                out.messages.addError(path + ": NOT_IN excludes all stores (condition can never be true)");
            }
        }
    }

    // ====== WARNINGS FOR "rule can be improved" ======
    private void addAndWarnings(List<Condition> children,
                                List<EvalResult> childResults,
                                Set<String> finalIntersection,
                                DomainIndexes idx,
                                String path,
                                ValidationResult messages) {

        // Warning type 1:
        // IN list contains values that can never happen because of other AND conditions
        for (int i = 0; i < children.size(); i++) {
            Condition child = children.get(i);
            String op = safeUpper(child.getOperator());
            if (!"IN".equals(op) && !"NOT_IN".equals(op)) continue;

            Dimension dim = resolveDimension(child.getProperty());
            if (dim == Dimension.UNKNOWN) continue;

            List<String> vals = child.getValue();
            if (vals == null || vals.isEmpty()) continue;

            // Base set from siblings only (AND context)
            Set<String> siblingIntersection = null;
            for (int j = 0; j < childResults.size(); j++) {
                if (j == i) continue;
                if (siblingIntersection == null) {
                    siblingIntersection = new HashSet<>(childResults.get(j).possibleStores);
                } else {
                    siblingIntersection.retainAll(childResults.get(j).possibleStores);
                }
            }
            if (siblingIntersection == null) siblingIntersection = new HashSet<>(idx.allStores);
            if (siblingIntersection.isEmpty()) continue; // already invalid conflict handled elsewhere

            if ("IN".equals(op)) {
                List<String> redundantValues = new ArrayList<>();
                for (String v : vals) {
                    Set<String> stores = storesForDimensionValue(dim, v, idx);
                    Set<String> overlap = new HashSet<>(stores);
                    overlap.retainAll(siblingIntersection);
                    if (overlap.isEmpty()) {
                        redundantValues.add(v);
                    }
                }
                if (!redundantValues.isEmpty() && redundantValues.size() < vals.size()) {
                    messages.addWarning(path + ".matchers[" + i + "]: values " + redundantValues
                            + " are unreachable due to other AND conditions; rule can be simplified");
                }
            } else { // NOT_IN
                List<String> redundantExclusions = new ArrayList<>();
                for (String v : vals) {
                    Set<String> stores = storesForDimensionValue(dim, v, idx);
                    Set<String> overlap = new HashSet<>(stores);
                    overlap.retainAll(siblingIntersection);
                    if (overlap.isEmpty()) {
                        redundantExclusions.add(v);
                    }
                }
                if (!redundantExclusions.isEmpty()) {
                    messages.addWarning(path + ".matchers[" + i + "]: NOT_IN values " + redundantExclusions
                            + " have no effect due to other AND conditions");
                }
            }
        }

        // Warning type 2 (optional but nice):
        // final intersection proves exact single banner/division, broader list can be narrowed
        Set<String> finalBanners = finalIntersection.stream()
                .map(idx.storeToBanner::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> finalDivisions = finalIntersection.stream()
                .map(idx.storeToDivision::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (finalIntersection.size() == 1) {
            String store = finalIntersection.iterator().next();
            messages.addWarning(path + ": AND resolves to a single store (" + store + "). Rule may be more specific than intended.");
        }
        if (finalBanners.size() == 1 && finalIntersection.size() > 0) {
            messages.addWarning(path + ": all matching stores are in banner '" + finalBanners.iterator().next()
                    + "' (rule can potentially be simplified)");
        }
        if (finalDivisions.size() == 1 && finalIntersection.size() > 0) {
            messages.addWarning(path + ": all matching stores are in division '" + finalDivisions.iterator().next()
                    + "' (rule can potentially be simplified)");
        }
    }

    // ====== CONFLICT EXPLANATION FOR INVALID AND ======
    private String explainAndConflict(List<Condition> children,
                                      List<EvalResult> childResults,
                                      DomainIndexes idx,
                                      String path) {

        // 1) Try pairwise disjoint explanation
        for (int i = 0; i < childResults.size(); i++) {
            for (int j = i + 1; j < childResults.size(); j++) {
                Set<String> a = childResults.get(i).possibleStores;
                Set<String> b = childResults.get(j).possibleStores;
                if (a.isEmpty() || b.isEmpty()) continue;

                Set<String> overlap = new HashSet<>(a);
                overlap.retainAll(b);
                if (overlap.isEmpty()) {
                    String detailed = explainPair(children.get(i), children.get(j), idx, path + ".matchers[" + i + "]", path + ".matchers[" + j + "]");
                    if (detailed != null) return detailed;

                    return path + ": AND block is invalid. No overlap between conditions ["
                            + childResults.get(i).summary + "] and [" + childResults.get(j).summary + "]";
                }
            }
        }

        // 2) Fallback
        return path + ": AND block is invalid (no possible store satisfies all nested conditions)";
    }

    private String explainPair(Condition a, Condition b, DomainIndexes idx, String aPath, String bPath) {
        // Provide better messages for leaf-vs-leaf combinations
        String opA = safeUpper(a.getOperator());
        String opB = safeUpper(b.getOperator());
        if (!isLeafOp(opA) || !isLeafOp(opB)) return null;

        Dimension da = resolveDimension(a.getProperty());
        Dimension db = resolveDimension(b.getProperty());

        // store IN + division IN/NOT_IN
        if (da == Dimension.STORE && db == Dimension.DIVISION) {
            return explainStoreVsDivision(a, b, idx, aPath, bPath);
        }
        if (db == Dimension.STORE && da == Dimension.DIVISION) {
            return explainStoreVsDivision(b, a, idx, bPath, aPath);
        }

        // store IN + banner IN/NOT_IN
        if (da == Dimension.STORE && db == Dimension.BANNER) {
            return explainStoreVsBanner(a, b, idx, aPath, bPath);
        }
        if (db == Dimension.STORE && da == Dimension.BANNER) {
            return explainStoreVsBanner(b, a, idx, bPath, aPath);
        }

        return null;
    }

    private String explainStoreVsDivision(Condition storeCond, Condition divisionCond, DomainIndexes idx,
                                          String storePath, String divisionPath) {
        String storeOp = safeUpper(storeCond.getOperator());
        String divOp = safeUpper(divisionCond.getOperator());

        List<String> stores = safeList(storeCond.getValue());
        List<String> divisions = safeList(divisionCond.getValue());

        if (stores.isEmpty() || divisions.isEmpty()) return null;

        // Try to find an explicit contradiction example
        for (String s : stores) {
            String actualDiv = idx.storeToDivision.get(s);
            if (actualDiv == null) continue;

            if ("IN".equals(storeOp) && "IN".equals(divOp)) {
                if (!divisions.contains(actualDiv)) {
                    return "Invalid AND: store '" + s + "' belongs to division '" + actualDiv
                            + "', but " + divisionPath + " requires division IN " + divisions;
                }
            }

            if ("IN".equals(storeOp) && "NOT_IN".equals(divOp)) {
                if (divisions.contains(actualDiv)) {
                    return "Invalid AND: store '" + s + "' belongs to division '" + actualDiv
                            + "', but " + divisionPath + " excludes that division (NOT_IN " + divisions + ")";
                }
            }
        }

        return null;
    }

    private String explainStoreVsBanner(Condition storeCond, Condition bannerCond, DomainIndexes idx,
                                        String storePath, String bannerPath) {
        String storeOp = safeUpper(storeCond.getOperator());
        String bannerOp = safeUpper(bannerCond.getOperator());

        List<String> stores = safeList(storeCond.getValue());
        List<String> banners = safeList(bannerCond.getValue());

        if (stores.isEmpty() || banners.isEmpty()) return null;

        for (String s : stores) {
            String actualBanner = idx.storeToBanner.get(s);
            if (actualBanner == null) continue;

            if ("IN".equals(storeOp) && "IN".equals(bannerOp)) {
                if (!banners.contains(actualBanner)) {
                    return "Invalid AND: store '" + s + "' belongs to banner '" + actualBanner
                            + "', but " + bannerPath + " requires banner IN " + banners;
                }
            }

            if ("IN".equals(storeOp) && "NOT_IN".equals(bannerOp)) {
                if (banners.contains(actualBanner)) {
                    return "Invalid AND: store '" + s + "' belongs to banner '" + actualBanner
                            + "', but " + bannerPath + " excludes that banner (NOT_IN " + banners + ")";
                }
            }
        }

        return null;
    }

    // ====== DIMENSION / INDEX HELPERS ======
    private Dimension resolveDimension(String property) {
        if (property == null) return Dimension.UNKNOWN;
        String p = property.toLowerCase(Locale.ROOT);

        // Adapt these checks to your exact field names
        if (p.contains("storeid") || p.endsWith(".store") || p.contains(".store.")) return Dimension.STORE;
        if (p.contains("division")) return Dimension.DIVISION;
        if (p.contains("banner")) return Dimension.BANNER;

        return Dimension.UNKNOWN;
    }

    private Set<String> storesForDimensionValue(Dimension dim, String value, DomainIndexes idx) {
        if (value == null) return Set.of();

        return switch (dim) {
            case STORE -> idx.storeToDivision.containsKey(value) ? Set.of(value) : Set.of();
            case DIVISION -> idx.divisionToStores.getOrDefault(value, Set.of());
            case BANNER -> idx.bannerToStores.getOrDefault(value, Set.of());
            default -> Set.of();
        };
    }

    private boolean isLeafOp(String op) {
        return "IN".equals(op) || "NOT_IN".equals(op);
    }

    private String leafSummary(Dimension d, String op, List<String> vals) {
        return d + " " + op + " " + vals;
    }

    private String safeUpper(String s) {
        return s == null ? "" : s.toUpperCase(Locale.ROOT);
    }

    private List<String> safeList(List<String> v) {
        return v == null ? List.of() : v;
    }
}
