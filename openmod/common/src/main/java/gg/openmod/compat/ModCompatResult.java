package gg.openmod.compat;

import java.util.List;

public record ModCompatResult(boolean compatible, List<String> missingMods) {}
