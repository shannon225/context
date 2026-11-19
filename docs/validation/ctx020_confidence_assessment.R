# CTX-020 round 2: peptide-level PEP calibration and precision-recall across seeds and builds.
# Method follows Ariana's figure_09_only_two_flavors.R and docs/validation/ctx020_peptide_calibration.py:
#   ten bins with edges (k/10)^2, left-closed/right-open except the last bin, which includes 1;
#   lower FDP = E/(T+E); upper FDP = min(lower * (1 + T_sched/E_sched), 1);
#   95% binom.test (Clopper-Pearson) intervals, with the upper end scaled by the same factor.
# Precision = T/(T+E), recall = T/96, ranked by native peptide score; tied scores enter together.
# Usage: Rscript ctx020_confidence_assessment.R <runs_dir> <inputs_dir> <out_dir>
suppressPackageStartupMessages({library(dplyr); library(tidyr); library(ggplot2); library(patchwork)})
args <- commandArgs(TRUE)
runs_dir <- args[1]; inputs_dir <- args[2]; out_dir <- args[3]
dir.create(out_dir, showWarnings = FALSE, recursive = TRUE)
base_font <- "Arial"   # fontconfig resolves Arial to the metric-compatible Liberation Sans here
breaks <- round((seq(0, 1, length.out = 11))^2, 8)
build_labels <- c(prefix = "Pre-CTX-020 + range fix", ctx = "CTX-020 (r5.3)")

read_assay <- function(seed) {
  f <- list.files(inputs_dir, pattern = paste0("_masked", seed, "_assay\\.txt$"), recursive = TRUE, full.names = TRUE)
  stopifnot(length(f) == 1)
  a <- read.delim(f, check.names = FALSE, stringsAsFactors = FALSE, strip.white = TRUE)
  # Bootstrapper writes N-terminal acetylation after residue 1 (known export bug); move it to the N terminus.
  tibble(sequence = sub("^([A-Z])(\\[\\+42\\.010565\\])", "\\2\\1", a$Compound), charge = as.integer(a$z),
         rt_center_s = a[["RT Time (min)"]] * 60, rt_half_s = a[["Window (min)"]] * 30,
         entrapment = tolower(trimws(a$isDecoy)) == "true")
}

read_peptides <- function(build, seed) {
  f <- Sys.glob(file.path(runs_dir, build, paste0("seed", seed), "percolator", "percolator", "*.peptide.reference.txt"))
  if (length(f) != 1) return(NULL)
  p <- read.delim(f, check.names = FALSE, stringsAsFactors = FALSE)
  id_parts <- strsplit(p$id, ":", fixed = TRUE)
  tibble(score = p$score, q = p[["q-value"]], pep = p$posterior_error_prob,
         sequence = sub("^-\\.(.*)\\.-$", "\\1", p$sequence),
         charge = as.integer(sub(".*\\+(\\d+)$", "\\1", p$id)),
         rt_s = as.numeric(vapply(id_parts, function(x) x[length(x) - 1], "")))
}

rows <- list(); unmatched <- list()
for (build in names(build_labels)) for (seed in c(1, 2, 22:28)) {
  p <- read_peptides(build, seed); if (is.null(p)) next
  a <- read_assay(seed)
  j <- left_join(p, a, by = c("sequence", "charge"))
  unmatched[[length(unmatched) + 1]] <- tibble(build = build, seed = seed, n_peptides = nrow(p), n_unmatched = sum(is.na(j$entrapment)))
  j <- j %>% filter(!is.na(entrapment)) %>%
    mutate(build = build, seed = seed, in_schedule = abs(rt_s - rt_center_s) <= rt_half_s)
  rows[[length(rows) + 1]] <- j
}
pep <- bind_rows(rows)
match_summary <- bind_rows(unmatched)
write.table(match_summary, file.path(out_dir, "assay-match-summary.tsv"), sep = "\t", quote = FALSE, row.names = FALSE)
write.table(pep, file.path(out_dir, "annotated-peptides.tsv"), sep = "\t", quote = FALSE, row.names = FALSE)

# Detection counts at the usual thresholds.
counts <- pep %>% group_by(build, seed) %>% summarise(
  peptides = n(), targets = sum(!entrapment), entrapments = sum(entrapment),
  T_pep01 = sum(!entrapment & pep < 0.01), E_pep01 = sum(entrapment & pep < 0.01),
  T_pep01_q01 = sum(!entrapment & pep < 0.01 & q < 0.01), E_pep01_q01 = sum(entrapment & pep < 0.01 & q < 0.01),
  T_q01 = sum(!entrapment & q <= 0.01), E_q01 = sum(entrapment & q <= 0.01),
  E_q01_in_schedule = sum(entrapment & q <= 0.01 & in_schedule), .groups = "drop")
write.table(counts, file.path(out_dir, "detection-counts.tsv"), sep = "\t", quote = FALSE, row.names = FALSE)

# Calibration bins; factor = 1 + T_sched/E_sched = 1 + 96/480.
factor <- 1 + 96 / 480
calib <- function(d) d %>% filter(is.finite(pep), pep >= 0, pep <= 1) %>%
  mutate(bin = cut(pep, breaks, right = FALSE, include.lowest = TRUE)) %>%
  mutate(bin = if_else(pep == 1, levels(bin)[10], as.character(bin))) %>%
  group_by(across(any_of(c("build", "seed"))), bin) %>%
  summarise(n = n(), E = sum(entrapment), T = n - E, mean_pep = mean(pep),
            lower = E / n, upper = min(lower * factor, 1),
            ci_low = binom.test(E, n)$conf.int[1], ci_high = min(binom.test(E, n)$conf.int[2] * factor, 1),
            .groups = "drop")
calib_seed <- calib(pep)
calib_pooled <- calib(pep %>% select(-seed))
write.table(calib_seed, file.path(out_dir, "calibration-by-seed.tsv"), sep = "\t", quote = FALSE, row.names = FALSE)
write.table(calib_pooled, file.path(out_dir, "calibration-pooled.tsv"), sep = "\t", quote = FALSE, row.names = FALSE)

# Precision-recall; ties enter together.
pr <- pep %>% group_by(build, seed, score) %>% summarise(t = sum(!entrapment), e = sum(entrapment), .groups = "drop") %>%
  arrange(build, seed, desc(score)) %>% group_by(build, seed) %>%
  mutate(T = cumsum(t), E = cumsum(e), precision = T / (T + E), recall = T / 96) %>% ungroup()
auprc <- pr %>% group_by(build, seed) %>% arrange(recall, .by_group = TRUE) %>%
  summarise(auprc = sum(diff(c(0, recall)) * precision), max_recall = max(recall),
            recall_at_precision_0.99 = max(c(0, recall[precision >= 0.99])), .groups = "drop")
write.table(auprc, file.path(out_dir, "pr-summary.tsv"), sep = "\t", quote = FALSE, row.names = FALSE)

paired <- auprc %>% select(build, seed, auprc) %>% pivot_wider(names_from = build, values_from = auprc) %>%
  left_join(counts %>% select(build, seed, T_pep01_q01) %>% pivot_wider(names_from = build, values_from = T_pep01_q01, names_prefix = "T_pep01_q01_"), by = "seed")
write.table(paired, file.path(out_dir, "paired-build-comparison.tsv"), sep = "\t", quote = FALSE, row.names = FALSE)

# Figures: theme_bw, Arial; one consistent style.
theme_ctx <- theme_bw(base_size = 10, base_family = base_font) + theme(legend.position = "bottom", strip.background = element_rect(fill = "grey95"))
cols <- c(prefix = "#0072B2", ctx = "#D55E00")
lab_build <- function(x) build_labels[x]

calib_plot <- function(d, title) {
  ggplot(d, aes(mean_pep, colour = build)) +
    geom_abline(linetype = "dotted", colour = "grey50") +
    geom_linerange(aes(ymin = ci_low, ymax = ci_high), alpha = .45, linewidth = .4) +
    geom_linerange(aes(ymin = lower, ymax = upper), alpha = .35, linewidth = 2) +
    geom_point(aes(y = lower), shape = 21, fill = "white", size = 1.8) +
    geom_point(aes(y = upper), shape = 17, size = 1.6) +
    scale_colour_manual(values = cols, labels = lab_build, name = NULL) +
    scale_x_continuous(labels = scales::percent, limits = c(0, 1)) +
    scale_y_continuous(labels = scales::percent, limits = c(0, 1)) +
    labs(x = "Mean peptide PEP within bin", y = "Entrapment FDP within bin", title = title) + theme_ctx
}
p_pooled <- calib_plot(calib_pooled, "Pooled across seeds") + facet_wrap(~build, labeller = as_labeller(build_labels))
p_seed <- calib_plot(calib_seed, "By seed") + facet_grid(build ~ seed, labeller = labeller(build = build_labels))
ggsave(file.path(out_dir, "calibration-pooled.pdf"), p_pooled, width = 7, height = 3.8, device = cairo_pdf)
ggsave(file.path(out_dir, "calibration-pooled.png"), p_pooled, width = 7, height = 3.8, dpi = 200)
ggsave(file.path(out_dir, "calibration-by-seed.pdf"), p_seed, width = 13, height = 4.5, device = cairo_pdf)
ggsave(file.path(out_dir, "calibration-by-seed.png"), p_seed, width = 13, height = 4.5, dpi = 200)

p_pr <- ggplot(pr, aes(recall, precision, colour = build, group = interaction(build, seed))) +
  geom_step(direction = "vh", alpha = .7) +
  scale_colour_manual(values = cols, labels = lab_build, name = NULL) +
  scale_x_continuous(labels = scales::percent, limits = c(0, 1)) + scale_y_continuous(labels = scales::percent) +
  labs(x = "Recall (scheduled true targets / 96)", y = "Precision T/(T+E)", title = "Precision-recall, one line per seed") + theme_ctx
p_counts <- counts %>% select(build, seed, T_pep01_q01, E_pep01_q01) %>%
  pivot_longer(c(T_pep01_q01, E_pep01_q01), names_to = "class", values_to = "n") %>%
  mutate(class = recode(class, T_pep01_q01 = "True targets", E_pep01_q01 = "Entrapments")) %>%
  ggplot(aes(factor(seed), n, fill = build)) + geom_col(position = position_dodge(.8), width = .75) +
  facet_wrap(~class, scales = "free_y") + scale_fill_manual(values = cols, labels = lab_build, name = NULL) +
  labs(x = "Seed", y = "Peptides at PEP < 0.01 and q < 0.01", title = "Detections at PEP < 0.01 and q < 0.01") + theme_ctx
p_sens <- p_pr / p_counts + plot_layout(heights = c(1.2, 1))
ggsave(file.path(out_dir, "sensitivity.pdf"), p_sens, width = 8, height = 8, device = cairo_pdf)
ggsave(file.path(out_dir, "sensitivity.png"), p_sens, width = 8, height = 8, dpi = 200)
print(match_summary); print(counts, width = Inf); print(auprc)
