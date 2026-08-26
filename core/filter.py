import fnmatch


class BlockFilter:
    """Filter blocks by name with support for wildcards and namespaces."""

    def __init__(self):
        self.keep_patterns = []
        self.remove_patterns = []
        self.replacement = "minecraft:air"

    def add_keep_pattern(self, pattern):
        self.keep_patterns.append(pattern.lower())

    def add_remove_pattern(self, pattern):
        self.remove_patterns.append(pattern.lower())

    def set_replacement(self, block_name):
        self.replacement = block_name

    def _matches(self, block_name, patterns):
        name = block_name.lower()
        for pattern in patterns:
            if "*" in pattern or "?" in pattern or "[" in pattern:
                if fnmatch.fnmatch(name, pattern):
                    return True
                path = name.split(":")[-1] if ":" in name else name
                pat_path = pattern.split(":")[-1] if ":" in pattern else pattern
                if fnmatch.fnmatch(path, pat_path):
                    return True
            else:
                if name == pattern:
                    return True
                path = name.split(":")[-1] if ":" in name else name
                pat_path = pattern.split(":")[-1] if ":" in pattern else pattern
                if path == pat_path:
                    return True
        return False

    def should_keep(self, block_name):
        if self.keep_patterns:
            return self._matches(block_name, self.keep_patterns)
        elif self.remove_patterns:
            return not self._matches(block_name, self.remove_patterns)
        else:
            return True

    def apply_to_litematic(self, litematic_file):
        return litematic_file.filter_blocks(
            keep_blocks=self._get_matching_names(litematic_file),
            remove_blocks=None,
            replacement=self.replacement,
        )

    def apply_remove(self, litematic_file):
        remove_set = set()
        for block_name in litematic_file.get_all_block_types():
            if not self.should_keep(block_name):
                remove_set.add(block_name)
        if not remove_set:
            return litematic_file
        return litematic_file.filter_blocks(
            keep_blocks=None,
            remove_blocks=remove_set,
            replacement=self.replacement,
        )

    def _get_matching_names(self, litematic_file):
        keep_set = set()
        for block_name in litematic_file.get_all_block_types():
            if self.should_keep(block_name):
                keep_set.add(block_name)
        return keep_set

    @classmethod
    def create_keep_only(cls, patterns, replacement="minecraft:air"):
        f = cls()
        for p in patterns:
            f.add_keep_pattern(p)
        f.set_replacement(replacement)
        return f

    @classmethod
    def create_remove(cls, patterns, replacement="minecraft:air"):
        f = cls()
        for p in patterns:
            f.add_remove_pattern(p)
        f.set_replacement(replacement)
        return f
