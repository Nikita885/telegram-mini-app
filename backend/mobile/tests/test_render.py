import time

from django.test import SimpleTestCase
from PIL import Image

from mobile.render import draw_layer


def marker_image():
    """100×100 transparent image with a red block in the top-left quarter."""
    img = Image.new("RGBA", (100, 100), (0, 0, 0, 0))
    img.paste((255, 0, 0, 255), (0, 0, 50, 50))
    return img


def red(out, x, y):
    r, g, b, _ = out.getpixel((x, y))
    return r > 200 and g < 60 and b < 60


class DrawLayerTests(SimpleTestCase):
    def canvas(self):
        return Image.new("RGBA", (400, 400), (255, 255, 255, 255))

    def test_places_box_centred(self):
        out = self.canvas()
        draw_layer(out, marker_image(), box_w=200, box_h=200, cx=200, cy=200)
        self.assertTrue(red(out, 150, 150))  # top-left quarter of the box
        self.assertFalse(red(out, 250, 250))
        self.assertEqual(out.getpixel((50, 50)), (255, 255, 255, 255))

    def test_flip_mirrors_horizontally(self):
        out = self.canvas()
        draw_layer(out, marker_image(), box_w=200, box_h=200, cx=200, cy=200, flipped=True)
        self.assertTrue(red(out, 250, 150))
        self.assertFalse(red(out, 150, 150))

    def test_rotation_is_clockwise(self):
        out = self.canvas()
        # Clockwise by 90°: the top-left quarter moves to the top-right.
        draw_layer(out, marker_image(), box_w=200, box_h=200, cx=200, cy=200, rotation=90)
        self.assertTrue(red(out, 250, 150))
        self.assertFalse(red(out, 150, 250))

    def test_huge_scale_is_bounded_by_output(self):
        out = self.canvas()
        started = time.monotonic()
        draw_layer(out, marker_image(), box_w=100_000, box_h=100_000, cx=200, cy=200, rotation=45)
        self.assertLess(time.monotonic() - started, 2)
        self.assertEqual(out.size, (400, 400))

    def test_layer_outside_output_is_skipped(self):
        out = self.canvas()
        draw_layer(out, marker_image(), box_w=50, box_h=50, cx=-500, cy=-500)
        self.assertEqual(out.getcolors(), [(160000, (255, 255, 255, 255))])
