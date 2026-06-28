plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.11" /* [SC] DO NOT EDIT */

// The source is kept in its 1.21.11 form (the only target on this branch for now). When the 1.21.x family gains
// more patches, add per-version replacements here that transform the 1.21.11-form source to the other patches,
// the same way the 26.x branch transforms its 26.2-form source down to 26.1.2.
stonecutter parameters {
    replacements {
    }
}
