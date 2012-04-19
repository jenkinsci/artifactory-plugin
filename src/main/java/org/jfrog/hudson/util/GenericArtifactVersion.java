package org.jfrog.hudson.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

/**
 * Represents a generic standard artifact version. This class can be used either for parsing a version string and/or for building
 * a version string programatically. Consistency is guaranteed at all times so that no invalid version is formed. Also, new
 * {@link GenericArtifactVersion}(versionString).toString().equals(versionString) is guaranteed to be always true if
 * 'versionString' is in the right format.
 * 
 * A standard artifact version is a string in the following format:
 * 
 * <pre>
 * <primary_numbers>[[-_]<annotation>][[-_]<annotation_revision>][[-_]<build_specifier>]
 * </pre>
 * <ul>
 * where:
 * <li>primary_numbers: <p1>[.<p2>[...<pn>]...] are non-negative integers separated by '.'. Examples: 1; 1.2; 1.2.3; etc.</li>
 * <li>annotation: Sequence of letters that usually describe the release type. Examples: alpha, beta, RC, etc.</li>
 * <li>annotation_revision: A non-negative integer associated to the annotation.</li>
 * <li>build_specifier: SNAPSHOT|YYYYMMdd.hhmmss-n[n[...[n]...]] Examples: SNAPSHOT, 20120110.152615-1, 20120110.152615-543</li>
 * </ul>
 * 
 * Note that we used generic terms as components. Even though "major", "minor", and "incremental" are terms used for the first
 * three primary components, we want to abstract that to be as generic as possible. Other implementations can reuse this class and
 * rename components as desired.
 * 
 * Notes: The version format is based on Maven's org.apache.maven.shared.release.versions.DefaultVersionInfo. As of today, that
 * class can be found in the following artifact: org.apache.maven.release:maven-release-manager:2.2
 * <p>
 * The reason why I decided to reimplement this is becase of the unwanted dependencies added with that artifact that clash with
 * some Jenkins' dependencies. Besides that, the implementation is not very flexible, it does not provide means to retrieve the
 * component separators and it cannot be extended either. Moreover, if we depended on that class, our code can easily break if we
 * upgrade that artifact and they update their version scheme. What we do when releasing is tied to our interpretation of version
 * schemes.
 * 
 * 
 * @author Nicolas Grobisa
 * 
 */
public class GenericArtifactVersion {

	private static final String SNAPSHOT_QUALIFIER = "SNAPSHOT";

	private static final Character DEFAULT_VERSION_COMPONENT_SEPARATOR = '-';

	private static final Integer DEFAULT_BASE_ANNOTATION_REVISION = 1;

	private static final String VERSION_COMPONENT_SEPARATOR_REGEX = "([-_]?)";

	private static final Pattern VERSION_COMPONENT_SEPARATOR_PATTERN = Pattern.compile(VERSION_COMPONENT_SEPARATOR_REGEX);

	private static final String VERSION_PRIMARY_NUMBERS_REGEX = "(\\d(\\.\\d)*)";

	private static final String VERSION_ANNOTATION_REGEX = "([a-zA-Z]+)";

	private static final Pattern VERSION_ANNOTATION_PATTERN = Pattern.compile(VERSION_ANNOTATION_REGEX);

	private static final String VERSION_ANNOTATIONREV_REGEX = "(\\d+)";

	private static final String VERSION_BUILD_SPECIFIER_REGEX = "((?i)" + SNAPSHOT_QUALIFIER + "|\\d{8}\\.\\d{6}\\-(\\d+))";

	private static final Pattern VERSION_BUILD_SPECIFIER_PATTERN = Pattern.compile(VERSION_BUILD_SPECIFIER_REGEX);

	private static final String VERSION_REGEX = "^" + VERSION_PRIMARY_NUMBERS_REGEX + "(" + VERSION_COMPONENT_SEPARATOR_REGEX
			+ VERSION_ANNOTATION_REGEX + ")?(" + VERSION_COMPONENT_SEPARATOR_REGEX + VERSION_ANNOTATIONREV_REGEX + ")?("
			+ VERSION_COMPONENT_SEPARATOR_REGEX + VERSION_BUILD_SPECIFIER_REGEX + ")?$";

	private static final int VERSION_REGEXGROUP_PRIMARYNUMS = 1;

	private static final int VERSION_REGEXGROUP_ANNOTATION_SEPARATOR = 4;

	private static final int VERSION_REGEXGROUP_ANNOTATION = 5;

	private static final int VERSION_REGEXGROUP_ANNOTATIONREV_SEPARATOR = 7;

	private static final int VERSION_REGEXGROUP_ANNOTATIONREV = 8;

	private static final int VERSION_REGEXGROUP_BUILDSPEC_SEPARATOR = 10;

	private static final int VERSION_REGEXGROUP_BUILDSPEC = 11;

	private static final Pattern VERSION_PATTERN = Pattern.compile(VERSION_REGEX);

	private final Integer[] primaryNumbers;

	private Character annotationSeparator;

	private String annotation;

	private Character annotationRevisionSeparator;

	private Integer annotationRevision;

	private Character buildSpecifierSeparator;

	private String buildSpecifier;

	public GenericArtifactVersion(final String versionString) {
		final Matcher matcher = VERSION_PATTERN.matcher(versionString);

		if (!matcher.matches()) {
			throw new IllegalArgumentException("Provided version string is not a valid version.");
		}

		Integer annotationRevision = parseAnnotationRevision(matcher.group(VERSION_REGEXGROUP_ANNOTATIONREV));
		Character annotationSeparator = parseSeparator(matcher.group(VERSION_REGEXGROUP_ANNOTATION_SEPARATOR));
		String annotation = nullIfBlank(matcher.group(VERSION_REGEXGROUP_ANNOTATION));
		Character buildSpecifierSeparator = parseSeparator(matcher.group(VERSION_REGEXGROUP_BUILDSPEC_SEPARATOR));
		String buildSpecifier = nullIfBlank(matcher.group(VERSION_REGEXGROUP_BUILDSPEC));

		// The regex will capture the snapshot qualifier as an annotation.
		// Therefore, we need to set it as the build specifier.
		if (buildSpecifier == null && SNAPSHOT_QUALIFIER.equalsIgnoreCase(annotation)) {
			buildSpecifierSeparator = annotationSeparator;
			buildSpecifier = annotation;
			annotationSeparator = null;
			annotation = null;
		}

		// Set the separators, using the default ones where appropriate. This is a subtle use case. The user might have provided a
		// version with annotation but no annotation revision. E.g., 1.0_alpha.
		// This means that we would know what the annotation separator is ('_' in the example) but we don't have information about
		// the annotation revision separator. If later the user wants to add an annotation revision, we want to be sure that the
		// default separator is used instead of the null separator. Example, if the users adds a revision of '5', then the result
		// would be "1.0_alpha-5", and not "1.0_alpha5".
		this.annotationSeparator = (annotation == null) ? DEFAULT_VERSION_COMPONENT_SEPARATOR : annotationSeparator;
		this.annotationRevisionSeparator = (annotationRevision == null) ? DEFAULT_VERSION_COMPONENT_SEPARATOR
				: parseSeparator(matcher.group(VERSION_REGEXGROUP_ANNOTATIONREV_SEPARATOR));
		this.buildSpecifierSeparator = (buildSpecifier == null) ? DEFAULT_VERSION_COMPONENT_SEPARATOR : buildSpecifierSeparator;

		// Set version components
		this.primaryNumbers = parsePrimaryNumber(matcher.group(VERSION_REGEXGROUP_PRIMARYNUMS));
		this.annotation = annotation;
		this.annotationRevision = annotationRevision;
		this.buildSpecifier = buildSpecifier;
	}

	/**
	 * Parses a primary component number.
	 * 
	 * @param primaryNumbers
	 *            A string representing a sequence of primary numbers separated by dot ('.'). Examples: "1", "1.2.3". Must be in
	 *            the correct format.
	 * @return An array where each element is an integer. Index 0 contains the most significant primary number.
	 */
	private Integer[] parsePrimaryNumber(final String primaryNumbers) {
		final String[] primaryNumberStrings = primaryNumbers.split("\\.");
		final int n = primaryNumberStrings.length;
		final Integer[] result = new Integer[n];

		for (int i = 0; i < n; i++) {
			result[i] = Integer.parseInt(primaryNumberStrings[i]);
		}

		return result;
	}

	/**
	 * Returns the input string or null if that string is blank or null.
	 * 
	 * @param string
	 *            The input string.
	 * @return The input string or null if that string is blank or null.
	 */
	private String nullIfBlank(final String string) {
		return StringUtils.isBlank(string) ? null : string;
	}

	/**
	 * Parses a version component separator string and returns it as a character.
	 * 
	 * @param separator
	 *            The separator string to parse.
	 * @return The first character of the string, or null if that string is empty.
	 */
	private Character parseSeparator(final String separator) {
		return StringUtils.isNotEmpty(separator) ? separator.charAt(0) : null;
	}

	/**
	 * Parses a string representing the version annotation revision.
	 * 
	 * @param annotationRev
	 *            The string to parse.
	 * @return An integer representing the version annotation revision, or null if the string is empty.
	 */
	private Integer parseAnnotationRevision(final String annotationRev) {
		return StringUtils.isNotEmpty(annotationRev) ? Integer.parseInt(annotationRev) : null;
	}

	/**
	 * Increments the least significant number in the version components by 1.
	 * <p>
	 * If the annotation revision is defined, it's incremented. Otherwise, the last primary number is incremented.
	 * <p>
	 * Examples:
	 * <ul>
	 * <li>"1.2.3.4-alpha-5" is upgraded to "1.2.3.4-alpha-6"</li>
	 * <li>"1.2.3.4-alpha" is upgraded to "1.2.3.5-alpha"</li>
	 * <li>"1-SNAPSHOT" is upgraded to "2-SNAPSHOT"</li>
	 * </ul>
	 * 
	 * @return The instance, for method chaining.
	 */
	public GenericArtifactVersion upgradeLeastSignificantNumber() {
		if (this.annotationRevision != null) {
			upgradeAnnotationRevision();
		} else {
			upgradeLeastSignificantPrimaryNumber();
		}

		return this;
	}

	/**
	 * Increments the least significant primary number by 1, resetting the annotation revision if it is defined.
	 * <p>
	 * Examples (assuming a base annotation revision of 1):
	 * <ul>
	 * <li>"1.2.3.4-alpha-5" is upgraded to "1.2.3.5-alpha-1"</li>
	 * <li>"1.2.3.4-alpha" is upgraded to "1.2.3.5-alpha"</li>
	 * <li>"1-SNAPSHOT" is upgraded to "2-SNAPSHOT"</li>
	 * </ul>
	 * 
	 * @return The instance, for method chaining.
	 */
	public GenericArtifactVersion upgradeLeastSignificantPrimaryNumber() {
		upgradePrimaryNumber(this.primaryNumbers.length - 1);

		return this;
	}

	/**
	 * Increments a primary number by 1, resetting the remaining less significant components.
	 * <p>
	 * Examples (assuming a base annotation revision of 1):
	 * <ul>
	 * <li>"1.2.3.4-alpha-5" is upgraded to "2.0.0.0-alpha-1" with an index of 0</li>
	 * <li>"1.2.3.4-alpha-5" is upgraded to "1.2.3.0-alpha-1" with an index of 3</li>
	 * <li>"1.2-alpha" is upgraded to "1.3-alpha" with an index of 1</li>
	 * <li>"1-SNAPSHOT" is upgraded to "2-SNAPSHOT" with an index of 0</li>
	 * </ul>
	 * 
	 * @param primaryNumberIndex
	 *            The index identifying the primary number to increase.
	 * @return The instance, for method chaining.
	 * @throws IllegalArgumentException
	 *             if the index is negative.
	 * @throws IllegalStateException
	 *             if this version doesn't have a primary number at the specified index.
	 */
	public GenericArtifactVersion upgradePrimaryNumber(final int primaryNumberIndex) {
		if (primaryNumberIndex < 0) {
			throw new IllegalArgumentException("Primary number index cannot be negative.");
		}
		if (this.primaryNumbers.length <= primaryNumberIndex) {
			throw new IllegalStateException("Cannot upgrade version becase '" + this + "' does not have a "
					+ "primary number at index " + primaryNumberIndex);
		}

		this.primaryNumbers[primaryNumberIndex]++;
		resetPrimaryNumbers(primaryNumberIndex + 1);
		resetAnnotationRevision();

		return this;
	}

	/**
	 * Increments the annotation revision by 1.
	 * 
	 * @return The instance, for method chaining.
	 * @throws IllegalStateException
	 *             if this version doesn't have an annotation revision defined.
	 */
	public GenericArtifactVersion upgradeAnnotationRevision() {
		if (this.annotationRevision == null) {
			throw new IllegalStateException("Cannot upgrade annotation revision for version '" + this
					+ "' because it does not have that component.");
		}

		this.annotationRevision++;
		return this;
	}

	/**
	 * Resets (sets to 0) all consecutive primary numbers starting at a specific index.
	 * 
	 * @param fromIndex
	 *            The index pointing to the first primary number that will be reset.
	 */
	private void resetPrimaryNumbers(final int fromIndex) {
		for (int i = fromIndex, n = this.primaryNumbers.length; i < n; i++) {
			this.primaryNumbers[i] = 0;
		}
	}

	/**
	 * Resets the annotation revision (if it is defined). It is set to {@link #DEFAULT_BASE_ANNOTATION_REVISION}.
	 */
	private void resetAnnotationRevision() {
		if (this.annotationRevision != null) {
			this.annotationRevision = DEFAULT_BASE_ANNOTATION_REVISION;
		}
	}

	/**
	 * Returns the string representation of the primary numbers.
	 * <p>
	 * The string representation is the concatenation of all numbers separated by the dot character ('.').
	 * 
	 * @return The primary numbers as a single string.
	 */
	public String getPrimaryNumbersAsString() {
		return createPrimaryNumbersString(new StringBuilder(8)).toString();
	}

	/**
	 * Returns how many primary numbers are defined for this version.
	 * 
	 * @return The primary number count.
	 */
	public int getPrimaryNumberCount() {
		return this.primaryNumbers.length;
	}

	/**
	 * Returns the primary number located at a specific index.
	 * 
	 * @param index
	 *            The index pointing to the primary number. Must be non-negative.
	 * @return The primary number, or null if it's not defined.
	 * @throws IllegalArgumentException
	 *             if the index is provided is negative.
	 */
	public Integer getPrimaryNumber(final int index) {
		if (index < 0) {
			throw new IllegalArgumentException("Primary number index must be non-negative.");
		}

		return (this.primaryNumbers.length > index) ? this.primaryNumbers[index] : null;
	}

	/**
	 * Defines the primary number located at a specific index.
	 * 
	 * @param index
	 *            The index pointing to the primary number to define. Must be non-negative.
	 * @param value
	 *            The number to define as the primary component. Must be non-negative.
	 * @return The instance, for method chaining.
	 * @throws IllegalArgumentException
	 *             if the index or value is negative, or if a primary number does not exist at that index.
	 */
	public GenericArtifactVersion setPrimaryNumber(final int index, final int value) {
		if (index < 0) {
			throw new IllegalArgumentException("Primary number index must be non-negative.");
		}
		if (index >= this.primaryNumbers.length) {
			throw new IllegalArgumentException("Invalid index for version digit. Maximum digit index for this version is "
					+ (this.primaryNumbers.length - 1));
		}
		if (value < 0) {
			throw new IllegalArgumentException("Primary numbers must be non-negative.");
		}

		this.primaryNumbers[index] = value;

		return this;
	}

	/**
	 * Returns the separator character for the version annotation component.
	 * 
	 * @return The annotation separator, or null if there's none.
	 */
	public Character getAnnotationSeparator() {
		return this.annotationSeparator;
	}

	/**
	 * Sets the separator character for the version annotation component.
	 * 
	 * @param annotationSeparator
	 *            A valid separator character (read this class' documentation for more information about the valid format). null
	 *            can be used to indicate that there's no separator.
	 * @return The instance, for method chaining.
	 * @throws IllegalArgumentException
	 *             if the character provided is invalid.
	 */
	public GenericArtifactVersion setAnnotationSeparator(final Character annotationSeparator) {
		validateSeparator(annotationSeparator);
		this.annotationSeparator = annotationSeparator;

		return this;
	}

	/**
	 * Returns the version annotation component.
	 * 
	 * @return The annotation, or null if it is not defined.
	 */
	public String getAnnotation() {
		return this.annotation;
	}

	/**
	 * Defines the version annotation component.
	 * 
	 * @param annotation
	 *            A valid annotation string (read this class' documentation for more information about the valid format). null can
	 *            be used to indicate that there's no annotation.
	 * @return The instance, for method chaining.
	 * @throws IllegalArgumentException
	 *             if the annotation is invalid.
	 */
	public GenericArtifactVersion setAnnotation(final String annotation) {
		if (annotation != null && !VERSION_ANNOTATION_PATTERN.matcher(annotation).matches()) {
			throw new IllegalArgumentException("Invalid version separator.");
		}
		this.annotation = annotation;

		return this;
	}

	/**
	 * Returns the separator character for the annotation revision.
	 * 
	 * @return The separator, or null if there's none.
	 */
	public Character getAnnotationRevisionSeparator() {
		return annotationRevisionSeparator;
	}

	/**
	 * Defines the separator character for the version annotation revision component.
	 * 
	 * @param annotationRevisionSeparator
	 *            A valid separator character (read this class' documentation for more information about the valid format). null
	 *            can be used to indicate that there's no separator.
	 * @return The instance, for method chaining.
	 * @throws IllegalArgumentException
	 *             if the character provided is invalid.
	 */
	public GenericArtifactVersion setAnnotationRevisionSeparator(final Character annotationRevisionSeparator) {
		validateSeparator(annotationRevisionSeparator);
		this.annotationRevisionSeparator = annotationRevisionSeparator;

		return this;
	}

	/**
	 * Returns the annotation revision number.
	 * 
	 * @return The revision number, or null if it's not defined.
	 */
	public Integer getAnnotationRevision() {
		return annotationRevision;
	}

	/**
	 * Defines the annotation revision number.
	 * 
	 * @param annotationRevision
	 *            The revision number. Must be non-negative. null can be used to indicate that there's no annotation revision
	 *            number.
	 * @return The instance, for method chaining.
	 * @throws IllegalArgumentException
	 *             if 'annotationRevision' is negative.
	 */
	public GenericArtifactVersion setAnnotationRevision(final Integer annotationRevision) {
		if (annotationRevision != null && annotationRevision < 0) {
			throw new IllegalArgumentException("Annotation revision must be non-negative.");
		}
		this.annotationRevision = annotationRevision;

		return this;
	}

	/**
	 * Returns the separator character for the build specifier component.
	 * 
	 * @return The separator, or null if there's none.
	 */
	public Character getBuildSpecifierSeparator() {
		return buildSpecifierSeparator;
	}

	/**
	 * Defines the separator character for the version build specifier component.
	 * 
	 * @param buildSpecifierSeparator
	 *            A valid separator character (read this class' documentation for more information about the valid format). null
	 *            can be used to indicate that there's no separator.
	 * @return The instance, for method chaining.
	 * @throws IllegalArgumentException
	 *             if the character provided is invalid.
	 */
	public GenericArtifactVersion setBuildSpecifierSeparator(final Character buildSpecifierSeparator) {
		validateSeparator(buildSpecifierSeparator);
		this.buildSpecifierSeparator = buildSpecifierSeparator;

		return this;
	}

	/**
	 * Returns the build specifier component.
	 * 
	 * @return The build specifier, or null if it's not defined.
	 */
	public String getBuildSpecifier() {
		return buildSpecifier;
	}

	/**
	 * Defines the build specifier component.
	 * 
	 * @param buildSpecifier
	 *            A valid build specifier (read this class' documentation for more information about the valid format). null can
	 *            be used to indicate that there's no build specifier component.
	 * @return The instance, for method chaining.
	 * @throws IllegalArgumentException
	 *             if the build specifier is invalid.
	 */
	public GenericArtifactVersion setBuildSpecifier(final String buildSpecifier) {
		if (buildSpecifier != null && !VERSION_BUILD_SPECIFIER_PATTERN.matcher(buildSpecifier).matches()) {
			throw new IllegalArgumentException("Invalid build specifier.");
		}
		this.buildSpecifier = buildSpecifier;

		return this;
	}

	/**
	 * Checks that a character is a valid version component separator, only if it's not null.
	 * 
	 * @param separator
	 *            The separator character that we want to check.
	 * @throws IllegalArgumentException
	 *             if the character is not a valid version component separator.
	 */
	private void validateSeparator(final Character separator) {
		if (separator != null && !VERSION_COMPONENT_SEPARATOR_PATTERN.matcher(String.valueOf(separator)).matches()) {
			throw new IllegalArgumentException("Invalid version separator.");
		}
	}

	/**
	 * Returns a string representation of this version object.
	 * <p>
	 * The following condition is always true: new {@link GenericArtifactVersion} (versionString).equals(versionString)
	 * <p>
	 * The result is the concatenation of all the version components, separated with their respective separator characters:
	 * <ul>
	 * <li>primary components separated by the dot character ('.').</li>
	 * <li>annotation separator</li>
	 * <li>annotation</li>
	 * <li>annotation revision separator</li>
	 * <li>annotation revision</li>
	 * <li>build specifier separator</li>
	 * <li>build specifier</li>
	 * </ul>
	 * 
	 */
	@Override
	public String toString() {
		final StringBuilder result = new StringBuilder(30);

		createPrimaryNumbersString(result);

		if (this.annotation != null) {
			if (this.annotationSeparator != null) {
				result.append(this.annotationSeparator);
			}
			result.append(this.annotation);
		}

		if (this.annotationRevision != null) {
			if (this.annotationRevisionSeparator != null) {
				result.append(this.annotationRevisionSeparator);
			}
			result.append(this.annotationRevision);
		}

		if (this.buildSpecifier != null) {
			if (this.buildSpecifierSeparator != null) {
				result.append(this.buildSpecifierSeparator);
			}
			result.append(this.buildSpecifier);
		}

		return result.toString();
	}

	/**
	 * Creates a string concatenating the primary numbers separated by the dot character ('.').
	 * <p>
	 * The result is appended to the provided string builder and returned.
	 * 
	 * @param result
	 *            The string builder where to append the string.
	 * @return The provided string builder.
	 */
	private StringBuilder createPrimaryNumbersString(final StringBuilder result) {
		for (int i = 0, n = this.primaryNumbers.length; i < n; i++) {
			if (i > 0) {
				result.append('.');
			}
			result.append(this.primaryNumbers[i]);
		}

		return result;
	}

}
