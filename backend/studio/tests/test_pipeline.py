import numpy as np
from django.test import SimpleTestCase
from PIL import Image, ImageDraw

from studio.pipeline import garment as G
from studio.pipeline.geometry import ThinPlateSpline, local_distortion, warp_rgba
from studio.pipeline.mannequin import build_canvas, compute_anchors


class GeometryTests(SimpleTestCase):
    def test_tps_interpolates_control_points(self):
        src = np.array([[0, 0], [100, 0], [0, 100], [100, 100], [50, 40]], dtype=float)
        dst = src * 1.5 + [10, 20]
        dst[4] += [5, -3]
        tps = ThinPlateSpline(src, dst)
        np.testing.assert_allclose(tps(src), dst, atol=1e-6)

    def test_similarity_has_no_distortion(self):
        src = np.array([[0, 0], [100, 0], [0, 100], [100, 100]], dtype=float)
        self.assertLess(local_distortion(src, src * 2 + 7, (0, 0, 100, 100)), 1e-3)

    def test_warp_moves_content(self):
        img = Image.new("RGBA", (40, 40), (255, 0, 0, 255))
        src = np.array([[0, 0], [40, 0], [0, 40], [40, 40]], dtype=float)
        out = warp_rgba(img, src, src + [100, 50], (200, 200))
        alpha = np.asarray(out.split()[-1])
        ys, xs = np.nonzero(alpha > 128)
        self.assertLessEqual(abs(int(xs.min()) - 100), 1)
        self.assertLessEqual(abs(int(ys.min()) - 50), 1)


class GarmentTests(SimpleTestCase):
    @classmethod
    def setUpClass(cls):
        super().setUpClass()
        silhouette = Image.open("../frontend/static/images/mannequin_male.png")
        cls.canvas = build_canvas(silhouette)
        cls.anchors, cls.profile = compute_anchors(cls.canvas)

    def test_anchors_are_ordered_top_to_bottom(self):
        a = self.anchors
        order = ("head_top", "neck", "shoulder_left", "waist_left", "hips_left", "knee_left", "ankle_left")
        ys = [a[k][1] for k in order]
        self.assertEqual(ys, sorted(ys))
        self.assertLess(a["shoulder_left"][0], 0.5)
        self.assertGreater(a["shoulder_right"][0], 0.5)

    def test_tshirt_fits_on_torso(self):
        img = Image.new("RGBA", (300, 360), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        d.polygon([(90, 0), (210, 0), (300, 80), (250, 120), (240, 360), (60, 360), (50, 120), (0, 80)],
                  fill=(20, 120, 200, 255))
        cutout = G.crop_to_content(img)
        kp = G.detect_keypoints(cutout, "upper")
        result = G.fit_garment(cutout, kp, "upper", self.anchors, self.profile, self.canvas.size)
        self.assertGreater(result.score, 0.7)
        alpha = np.asarray(result.layer.split()[-1]) > 128
        ys, _ = np.nonzero(alpha)
        shoulder_y = self.anchors["shoulder_left"][1] * self.canvas.height
        self.assertLess(ys.min(), shoulder_y)

    def test_colors_named_in_russian(self):
        img = Image.new("RGBA", (50, 50), (200, 30, 30, 255))
        self.assertEqual(G.dominant_colors(img)[0]["name"], "красный")

    def test_single_shoe_becomes_pair(self):
        img = Image.new("RGBA", (200, 80), (0, 0, 0, 0))
        ImageDraw.Draw(img).ellipse([0, 10, 199, 70], fill=(0, 0, 0, 255))
        self.assertGreater(G.pair_single_shoe(img).width, 380)

    def test_empty_photo_is_reported(self):
        with self.assertRaises(G.PipelineError):
            G.clean_alpha(Image.new("RGBA", (50, 50), (0, 0, 0, 0)))
