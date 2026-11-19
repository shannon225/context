# Reproduce the CTX020-045 fixed-feature training variability figure.
# Usage: Rscript docs/validation/ctx020_noise_floor_figure.R [data/output directory]
suppressPackageStartupMessages({library(ggplot2); library(dplyr); library(patchwork)})
args <- commandArgs(TRUE)
directory <- if (length(args)) args[1] else "docs/validation/ctx020-noise-floor-2026-11-19"
metrics <- read.delim(file.path(directory, "metrics-by-training-seed.tsv"))
assays <- read.delim(file.path(directory, "metrics-by-assay-and-training-seed.tsv"))
metrics <- metrics %>% filter(replicate != "train1b") %>% mutate(training = as.integer(substr(replicate, 6, 6)))
assays <- assays %>% filter(replicate != "train1b") %>% mutate(training = as.integer(substr(replicate, 6, 6)))
stopifnot(nrow(metrics) == 5, nrow(assays) == 45, !anyDuplicated(assays[c("training", "assay_seed")]))
reference <- assays %>% filter(training == 1) %>% select(assay_seed, baseline = T_pep01)
assays <- assays %>% left_join(reference, by = "assay_seed") %>%
  mutate(change = T_pep01 - baseline, assay = factor(assay_seed, levels = rev(c(1,2,22:28))))
theme_set(theme_bw(base_size = 11, base_family = "Arial"))
theme_update(plot.title = element_text(face = "bold", size = 12),
             plot.subtitle = element_text(size = 10), panel.grid.minor = element_blank(),
             plot.margin = margin(8, 12, 8, 8))
seed_axis <- scale_x_continuous(breaks = 1:5, limits = c(0.6,5.4))
a <- ggplot(assays, aes(training, assay, fill = change)) +
  geom_tile(colour = "white", linewidth = 1) + geom_text(aes(label = T_pep01), size = 3.7) +
  scale_fill_gradient2(low = "#7AB6D6", mid = "white", high = "#EAA66A", midpoint = 0,
                       limits = c(-3,3), breaks = -3:3, name = "Change from seed 1") +
  scale_x_continuous(breaks = 1:5, expand = c(0,0)) +
  scale_y_discrete(expand = c(0,0)) +
  labs(title = "A  Target recovery", subtitle = "True targets at PEP < 0.01; median = 63 for every training seed",
       x = "Background-training seed", y = "Assay dataset seed") +
  theme(panel.grid = element_blank(), legend.position = "bottom", legend.key.width = grid::unit(1.1,"cm"))
b <- ggplot(metrics, aes(training, median_auprc)) +
  geom_point(size = 3, colour = "#0072B2") +
  geom_text(aes(label = sprintf("%.5f", median_auprc)), vjust = -1, size = 3.4) + seed_axis +
  scale_y_continuous(limits = c(0.9583,0.9618), breaks = seq(0.9585,0.9615,0.001)) +
  labs(title = "B  Sensitivity", subtitle = "Median across nine assays; maximum recall unchanged in every assay",
       x = "Background-training seed", y = "Median area under precision–recall curve")
fdp <- bind_rows(metrics %>% mutate(estimate = "Lower", fdp = pooled_pep01_upper_fdp / 1.2),
                 metrics %>% mutate(estimate = "Upper", fdp = pooled_pep01_upper_fdp))
c <- ggplot(metrics, aes(training)) +
  geom_linerange(aes(ymin = pooled_pep01_upper_fdp/1.2, ymax = pooled_pep01_upper_fdp), colour = "grey55", linewidth = 1) +
  geom_point(data = fdp, aes(y = fdp, shape = estimate, colour = estimate), size = 3) +
  scale_shape_manual(values = c(Lower = 1, Upper = 17)) +
  scale_colour_manual(values = c(Lower = "#0072B2", Upper = "#D55E00")) + seed_axis +
  scale_y_continuous(labels = scales::label_percent(accuracy = .1), limits = c(0,0.012)) +
  labs(title = "C  Entrapment FDP", subtitle = "PEP < 0.01; pooled within each training seed across nine assays",
       x = "Background-training seed", y = "Entrapment false discovery proportion", shape = NULL, colour = NULL) +
  theme(legend.position = "bottom")
d <- ggplot(metrics, aes(training, 100 * weighted_abs_calibration_error)) +
  geom_point(size = 3, colour = "#0072B2") +
  geom_text(aes(label = paste(bins_ci_above_diagonal, "bins")), vjust = -1, size = 3.5) + seed_axis +
  scale_y_continuous(limits = c(2.50,2.88)) +
  labs(title = "D  Calibration", subtitle = "Labels: bins with lower 95% FDP bound > mean PEP (all ten bins)",
       x = "Background-training seed", y = "Weighted mean absolute error (percentage points)")
figure <- ((a | b) / (c | d)) + plot_annotation(
  title = "CTX-020 r5.3: variability from retraining on fixed features",
  subtitle = "Five background-training seeds applied to the same nine assay datasets",
  caption = paste("54 successful runs: 9 assays × (5 training seeds + a second seed-1 run). Repeated reports and model weights were byte-identical.",
                  "C: segments span lower/upper entrapment estimates, not confidence intervals. D: error = count-weighted |lower FDP − mean PEP|.",
                  "Empirical variation before the r5.4 fix only; repeated trainings are not independent experimental replicates. 19 Nov 2026.", sep = "\n"),
  theme = theme(plot.title = element_text(face = "bold", size = 17),
                plot.subtitle = element_text(size = 12), plot.caption = element_text(hjust = 0, size = 10)))
ggsave(file.path(directory, "training-variability.png"), figure, width = 13, height = 10, dpi = 200)
ggsave(file.path(directory, "training-variability.pdf"), figure, width = 13, height = 10, device = cairo_pdf)
