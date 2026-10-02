/**
 * Contract between the core and the format modules. Not API: types here may change or disappear in
 * any version. The module descriptor exports this package only to the format modules.
 *
 * <p>Each format module has one package with exactly one public type, the
 * {@link io.github.yuraburyakov.casttomarkdown.internal.DocumentConverter} implementation;
 * everything else in that package is package-private.
 */
package io.github.yuraburyakov.casttomarkdown.internal;
