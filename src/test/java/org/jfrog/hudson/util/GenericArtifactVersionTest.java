package org.jfrog.hudson.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/**
 * Unit test for {@link GenericArtifactVersion}.
 * 
 * @author Nicolas Grobisa
 * 
 */
public class GenericArtifactVersionTest {

	/**
	 * Case: success: parse a snapshot version that includes all of the components.
	 */
	@Test
	public void parse_success_snapshot_full_withSeparator() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1.2.3_alpha_4-snapshot");
		assertEquals("1.2.3_alpha_4-snapshot", version.toString());
		assertEquals(3, version.getPrimaryNumberCount());
		assertEquals(Integer.valueOf(1), version.getPrimaryNumber(0));
		assertEquals(Integer.valueOf(2), version.getPrimaryNumber(1));
		assertEquals(Integer.valueOf(3), version.getPrimaryNumber(2));
		assertEquals(Character.valueOf('_'), version.getAnnotationSeparator());
		assertEquals("alpha", version.getAnnotation());
		assertEquals(Character.valueOf('_'), version.getAnnotationRevisionSeparator());
		assertEquals(Integer.valueOf(4), version.getAnnotationRevision());
		assertEquals(Character.valueOf('-'), version.getBuildSpecifierSeparator());
		assertEquals("snapshot", version.getBuildSpecifier());
	}

	/**
	 * Case: success: parse a timestamped snapshot version that includes all of the components.
	 */
	@Test
	public void parse_success_timestampedSnapshot_full_withSeparator() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1.2.3_alpha_4-20120111.155625-1");
		assertEquals("1.2.3_alpha_4-20120111.155625-1", version.toString());
		assertEquals(3, version.getPrimaryNumberCount());
		assertEquals(Integer.valueOf(1), version.getPrimaryNumber(0));
		assertEquals(Integer.valueOf(2), version.getPrimaryNumber(1));
		assertEquals(Integer.valueOf(3), version.getPrimaryNumber(2));
		assertEquals(Character.valueOf('_'), version.getAnnotationSeparator());
		assertEquals("alpha", version.getAnnotation());
		assertEquals(Character.valueOf('_'), version.getAnnotationRevisionSeparator());
		assertEquals(Integer.valueOf(4), version.getAnnotationRevision());
		assertEquals(Character.valueOf('-'), version.getBuildSpecifierSeparator());
		assertEquals("20120111.155625-1", version.getBuildSpecifier());
	}

	/**
	 * Case: success: Parse a snapshot version that uses all of the components but no separators.
	 */
	@Test
	public void parse_success_snapshot_full_noSeparators() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1.2.3alpha4snapshot");
		assertEquals("1.2.3alpha4snapshot", version.toString());
		assertEquals(3, version.getPrimaryNumberCount());
		assertEquals(Integer.valueOf(1), version.getPrimaryNumber(0));
		assertEquals(Integer.valueOf(2), version.getPrimaryNumber(1));
		assertEquals(Integer.valueOf(3), version.getPrimaryNumber(2));
		assertNull(version.getAnnotationSeparator());
		assertEquals("alpha", version.getAnnotation());
		assertNull(version.getAnnotationRevisionSeparator());
		assertEquals(Integer.valueOf(4), version.getAnnotationRevision());
		assertNull(version.getBuildSpecifierSeparator());
		assertEquals("snapshot", version.getBuildSpecifier());
	}

	/**
	 * Case: success: Parse a fixed version that only contains primary components.
	 */
	@Test
	public void parse_success_fixed_justPrimary() {
		GenericArtifactVersion version = new GenericArtifactVersion("1.2.3.4.5");
		assertEquals("1.2.3.4.5", version.toString());
		assertEquals(5, version.getPrimaryNumberCount());
		assertEquals(Integer.valueOf(1), version.getPrimaryNumber(0));
		assertEquals(Integer.valueOf(2), version.getPrimaryNumber(1));
		assertEquals(Integer.valueOf(3), version.getPrimaryNumber(2));
		assertEquals(Integer.valueOf(4), version.getPrimaryNumber(3));
		assertEquals(Integer.valueOf(5), version.getPrimaryNumber(4));
		assertEquals(Character.valueOf('-'), version.getAnnotationSeparator());
		assertNull(version.getAnnotation());

		version = new GenericArtifactVersion("1");
		assertEquals(1, version.getPrimaryNumberCount());
		assertEquals(Integer.valueOf(1), version.getPrimaryNumber(0));
		assertEquals(Character.valueOf('-'), version.getAnnotationSeparator());
		assertNull(version.getAnnotation());
	}

	/**
	 * Case: failure: Version contains no primary components.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void parse_fail_noPrimaryNumbers() {
		new GenericArtifactVersion("alpha_4-snapshot");
	}

	/**
	 * Case: failure: Version contains an annotation separator but no annotation.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void parse_fail_annotationSeparatorWithoutAnnotation() {
		new GenericArtifactVersion("1.2.3-");
	}

	/**
	 * Case: failure: Version contains an annotation revision separator but no annotation revision.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void parse_fail_annotationRevSeparatorWithoutAnnotationRev() {
		new GenericArtifactVersion("1.2.3-alpha-");
	}

	/**
	 * Case: failure: Version contains a build specifier separator but no build specifier.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void parse_fail_buildSpecSeparatorWithoutBuildSpec() {
		new GenericArtifactVersion("1.2.3-alpha-4-");
	}

	/**
	 * Case: success: Build a snapshot version using the builder methods.
	 */
	@Test
	public void build_success_fullVersion() {
		final GenericArtifactVersion version = new GenericArtifactVersion("4.5.6");
		version.setAnnotationSeparator('-');
		version.setAnnotation("alpha");
		version.setAnnotationRevisionSeparator('-');
		version.setAnnotationRevision(4);
		version.setBuildSpecifierSeparator('-');
		version.setBuildSpecifier("SNAPSHOT");

		assertEquals("4.5.6-alpha-4-SNAPSHOT", version.toString());
		version.setPrimaryNumber(0, 1);
		version.setPrimaryNumber(1, 2);
		version.setPrimaryNumber(2, 3);
		assertEquals("1.2.3-alpha-4-SNAPSHOT", version.toString());
		assertEquals(3, version.getPrimaryNumberCount());
		assertEquals(Integer.valueOf(1), version.getPrimaryNumber(0));
		assertEquals(Integer.valueOf(2), version.getPrimaryNumber(1));
		assertEquals(Integer.valueOf(3), version.getPrimaryNumber(2));
		assertEquals(Character.valueOf('-'), version.getAnnotationSeparator());
		assertEquals("alpha", version.getAnnotation());
		assertEquals(Character.valueOf('-'), version.getAnnotationRevisionSeparator());
		assertEquals(Integer.valueOf(4), version.getAnnotationRevision());
		assertEquals(Character.valueOf('-'), version.getBuildSpecifierSeparator());
		assertEquals("SNAPSHOT", version.getBuildSpecifier());
	}

	/**
	 * Case: success: Build a snapshot version with the default separators.
	 */
	@Test
	public void build_success_fullVersion_useDefaultSeparators() {
		GenericArtifactVersion version = new GenericArtifactVersion("4.5.6");
		version.setAnnotation("alpha");
		version.setAnnotationRevision(4);
		version.setBuildSpecifier("SNAPSHOT");
		assertEquals("4.5.6-alpha-4-SNAPSHOT", version.toString());

		version = new GenericArtifactVersion("4.5.6_SNAPSHOT");
		version.setAnnotation("beta");
		version.setAnnotationRevision(2);
		assertEquals("4.5.6-beta-2_SNAPSHOT", version.toString());
	}

	/**
	 * Case: failure: Attempt to modify a primary number that doesn't exist.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void build_fail_invalidPrimaryNumberIndex() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1");
		version.setPrimaryNumber(1, 5);
	}

	/**
	 * Case: failure: Attempt to use an invalid separator character.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void build_fail_invalidSeparator() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1");
		version.setAnnotationSeparator('+');
	}

	/**
	 * Case: failure: Attempt to set an invalid annotation.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void build_fail_invalidAnnotation() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1");
		version.setAnnotation("illegal9annotation");
	}

	/**
	 * Case: failure: Attempt to set an invalid annotation revision.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void build_fail_invalidAnnotationRevision() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1");
		version.setAnnotationRevision(-1);
	}

	/**
	 * Case: failure: Attempt to set an invalid build specifier.
	 */
	@Test(expected = IllegalArgumentException.class)
	public void build_fail_invalidBuildSpecifier() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1");
		version.setBuildSpecifier("somestring");
	}

	/**
	 * Tests {@link GenericArtifactVersion#upgradeLeastSignificantNumber()}. Case: success
	 */
	@Test
	public void upgradeLeastSignificantNumber_success() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1.2.3.4.5_beta1-SNAPSHOT");
		version.upgradeLeastSignificantNumber();
		assertEquals("1.2.3.4.5_beta2-SNAPSHOT", version.toString());

		version.setAnnotationRevision(null);
		version.upgradeLeastSignificantNumber();
		assertEquals("1.2.3.4.6_beta-SNAPSHOT", version.toString());
	}

	/**
	 * Tests {@link GenericArtifactVersion#upgradeLeastSignificantPrimaryNumber()}. Case: success
	 */
	@Test
	public void upgradeLeastSignificantPrimaryNumber_success() {
		final GenericArtifactVersion version = new GenericArtifactVersion("1.2.3.4.5_beta1-SNAPSHOT");
		version.upgradeLeastSignificantPrimaryNumber();
		assertEquals("1.2.3.4.6_beta1-SNAPSHOT", version.toString());
	}

	/**
	 * Tests {@link GenericArtifactVersion#upgradePrimaryNumber(int)}. Case: success
	 */
	@Test
	public void upgradePrimaryNumber_success() {
		GenericArtifactVersion version = new GenericArtifactVersion("1.2.3.4.5_beta9-SNAPSHOT");
		version.upgradePrimaryNumber(0);
		assertEquals("2.0.0.0.0_beta1-SNAPSHOT", version.toString());

		version = new GenericArtifactVersion("1.2.3.4.5_beta9-SNAPSHOT");
		version.upgradePrimaryNumber(4);
		assertEquals("1.2.3.4.6_beta1-SNAPSHOT", version.toString());
	}

}
