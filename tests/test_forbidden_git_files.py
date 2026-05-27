import unittest

from scripts.check_forbidden_git_files import find_forbidden_paths


class ForbiddenGitFilesTest(unittest.TestCase):
    def test_allows_normal_project_files(self):
        paths = [
            ".gitignore",
            "app/src/main/java/com/omniveye/app/MainActivity.kt",
            "cloud-backend/omnieye_cloud/main.py",
        ]

        self.assertEqual([], find_forbidden_paths(paths, max_size_bytes=100_000_000))

    def test_flags_insta360_sdk_and_model_artifacts(self):
        paths = [
            "Insta360 X4 SDK/libcamera.aar",
            "third_party/CameraSDK/native/libcamera.so",
            "models/DAP-weights/model.pth",
            "赛事SDK包（Android+iOS）(2).rar",
        ]

        violations = find_forbidden_paths(paths, max_size_bytes=100_000_000)

        self.assertEqual(paths, [violation.path for violation in violations])

    def test_flags_large_files_by_size(self):
        paths = ["docs/demo.mp4"]
        sizes = {"docs/demo.mp4": 150_000_000}

        violations = find_forbidden_paths(
            paths,
            max_size_bytes=100_000_000,
            size_lookup=sizes.get,
        )

        self.assertEqual(["docs/demo.mp4"], [violation.path for violation in violations])


if __name__ == "__main__":
    unittest.main()
