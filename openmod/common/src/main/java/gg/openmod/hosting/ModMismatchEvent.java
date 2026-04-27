package gg.openmod.hosting;

import java.util.List;

public record ModMismatchEvent(List<String> missingMods) {}
