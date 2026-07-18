package io.github.moncefdev.shulkerinventory.client;

import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

// The GUI lighting rig correction, DERIVED at runtime from vanilla's own light rigs instead of duplicating their
// values. LightingRigCaptureMixin captures the final transformed light directions vanilla computes for the
// ITEMS_FLAT and ITEMS_3D rigs (both are orthogonal transforms of the same two-light base, so the pairs are
// congruent); this class rebuilds the rotation mapping the flat pair onto the 3D pair from the two captured
// pairs (orthonormal frame of each pair, correction = F_3D x F_FLAT^T). Rotating a lighting normal by that
// correction makes its diffuse response under the ITEMS_3D rig equal its response under ITEMS_FLAT, for any
// geometry - see LayerRenderStateContentMixin. The frame construction yields a proper rotation even where the
// exact rig quotient contains a mirror; the difference lies entirely along the direction orthogonal to both
// lights, which contributes nothing to either diffuse dot product, so the lighting response is identical.
// Whatever values a Minecraft update (or anything else re-tuning the rigs) uses, the correction follows.
public final class GuiLightRigs {
	private GuiLightRigs() {}

	// Identity until both rigs are captured; the Lighting constructor runs during client init, before any GUI
	// rendering, so real renders always see the derived correction. Written once from the capture, read on the
	// render thread.
	private static final Matrix3f FLAT_TO_3D = new Matrix3f();

	private static Vector3f flatLight0;
	private static Vector3f flatLight1;
	private static Vector3f itemsLight0;
	private static Vector3f itemsLight1;

	public static void captureFlatRig(Vector3fc light0, Vector3fc light1) {
		flatLight0 = new Vector3f(light0);
		flatLight1 = new Vector3f(light1);
		derive();
	}

	public static void capture3dRig(Vector3fc light0, Vector3fc light1) {
		itemsLight0 = new Vector3f(light0);
		itemsLight1 = new Vector3f(light1);
		derive();
	}

	public static Matrix3fc flatTo3d() {
		return FLAT_TO_3D;
	}

	private static void derive() {
		if (flatLight0 == null || itemsLight0 == null) {
			return;
		}
		FLAT_TO_3D.set(frame(itemsLight0, itemsLight1).mul(frame(flatLight0, flatLight1).transpose()));
	}

	// Orthonormal frame spanned by a light pair: e1 along the first light, e2 the second light's orthogonal
	// component, e3 their cross product.
	private static Matrix3f frame(Vector3fc a, Vector3fc b) {
		Vector3f e1 = new Vector3f(a).normalize();
		Vector3f e2 = new Vector3f(b).sub(new Vector3f(e1).mul(e1.dot(b))).normalize();
		Vector3f e3 = new Vector3f(e1).cross(e2);
		return new Matrix3f(e1, e2, e3);
	}
}
