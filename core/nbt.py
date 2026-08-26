import struct


TAG_END = 0
TAG_BYTE = 1
TAG_SHORT = 2
TAG_INT = 3
TAG_LONG = 4
TAG_FLOAT = 5
TAG_DOUBLE = 6
TAG_BYTE_ARRAY = 7
TAG_STRING = 8
TAG_LIST = 9
TAG_COMPOUND = 10
TAG_INT_ARRAY = 11
TAG_LONG_ARRAY = 12


class NBTReader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def read(self, n):
        if self.pos + n > len(self.data):
            raise ValueError("Unexpected end of NBT data")
        result = self.data[self.pos:self.pos + n]
        self.pos += n
        return result

    def read_byte(self):
        return struct.unpack(">b", self.read(1))[0]

    def read_ubyte(self):
        return struct.unpack(">B", self.read(1))[0]

    def read_short(self):
        return struct.unpack(">h", self.read(2))[0]

    def read_ushort(self):
        return struct.unpack(">H", self.read(2))[0]

    def read_int(self):
        return struct.unpack(">i", self.read(4))[0]

    def read_uint(self):
        return struct.unpack(">I", self.read(4))[0]

    def read_long(self):
        return struct.unpack(">q", self.read(8))[0]

    def read_ulong(self):
        return struct.unpack(">Q", self.read(8))[0]

    def read_float(self):
        return struct.unpack(">f", self.read(4))[0]

    def read_double(self):
        return struct.unpack(">d", self.read(8))[0]

    def read_string(self):
        length = self.read_ushort()
        raw = self.read(length)
        return raw.decode("utf-8")

    def read_tag_payload(self, tag_type):
        if tag_type == TAG_BYTE:
            return self.read_byte()
        elif tag_type == TAG_SHORT:
            return self.read_short()
        elif tag_type == TAG_INT:
            return self.read_int()
        elif tag_type == TAG_LONG:
            return self.read_long()
        elif tag_type == TAG_FLOAT:
            return self.read_float()
        elif tag_type == TAG_DOUBLE:
            return self.read_double()
        elif tag_type == TAG_BYTE_ARRAY:
            length = self.read_uint()
            return list(self.read(length))
        elif tag_type == TAG_STRING:
            return self.read_string()
        elif tag_type == TAG_LIST:
            element_type = self.read_ubyte()
            length = self.read_uint()
            return [self.read_tag_payload(element_type) for _ in range(length)]
        elif tag_type == TAG_COMPOUND:
            result = {}
            while True:
                child_type = self.read_ubyte()
                if child_type == TAG_END:
                    break
                name = self.read_string()
                result[name] = self.read_tag_payload(child_type)
            return result
        elif tag_type == TAG_INT_ARRAY:
            length = self.read_uint()
            return [self.read_int() for _ in range(length)]
        elif tag_type == TAG_LONG_ARRAY:
            length = self.read_uint()
            return [self.read_long() for _ in range(length)]
        else:
            raise ValueError(f"Unknown NBT tag type: {tag_type}")

    def read_root(self):
        root_type = self.read_ubyte()
        if root_type != TAG_COMPOUND:
            raise ValueError(f"Root must be TAG_Compound (10), got {root_type}")
        name = self.read_string()
        value = self.read_tag_payload(TAG_COMPOUND)
        return {name: value}


def unsigned_to_signed_64(value):
    value &= 0xFFFFFFFFFFFFFFFF
    if value >= 0x8000000000000000:
        value -= 0x10000000000000000
    return value


def signed_to_unsigned_64(value):
    return value & 0xFFFFFFFFFFFFFFFF


class NBTWriter:
    def __init__(self):
        self.buffer = bytearray()

    def write(self, data):
        self.buffer.extend(data)

    def write_byte(self, value):
        self.buffer.extend(struct.pack(">b", value))

    def write_ubyte(self, value):
        self.buffer.extend(struct.pack(">B", value))

    def write_short(self, value):
        self.buffer.extend(struct.pack(">h", value))

    def write_ushort(self, value):
        self.buffer.extend(struct.pack(">H", value))

    def write_int(self, value):
        self.buffer.extend(struct.pack(">i", value))

    def write_uint(self, value):
        self.buffer.extend(struct.pack(">I", value))

    def write_long(self, value):
        self.buffer.extend(struct.pack(">q", value))

    def write_ulong(self, value):
        self.buffer.extend(struct.pack(">Q", value))

    def write_float(self, value):
        self.buffer.extend(struct.pack(">f", value))

    def write_double(self, value):
        self.buffer.extend(struct.pack(">d", value))

    def write_string(self, value):
        encoded = value.encode("utf-8")
        self.write_ushort(len(encoded))
        self.buffer.extend(encoded)

    def write_tag_payload(self, tag_type, value):
        if tag_type == TAG_BYTE:
            self.write_byte(value)
        elif tag_type == TAG_SHORT:
            self.write_short(value)
        elif tag_type == TAG_INT:
            self.write_int(value)
        elif tag_type == TAG_LONG:
            self.write_long(value)
        elif tag_type == TAG_FLOAT:
            self.write_float(value)
        elif tag_type == TAG_DOUBLE:
            self.write_double(value)
        elif tag_type == TAG_BYTE_ARRAY:
            self.write_uint(len(value))
            self.buffer.extend(bytes(value))
        elif tag_type == TAG_STRING:
            self.write_string(value)
        elif tag_type == TAG_LIST:
            if not value:
                self.write_ubyte(TAG_END)
                self.write_uint(0)
            else:
                elem_type = _infer_list_element_type(value[0])
                self.write_ubyte(elem_type)
                self.write_uint(len(value))
                for item in value:
                    self.write_tag_payload(elem_type, item)
        elif tag_type == TAG_COMPOUND:
            for key, (child_type, child_value) in _infer_compound_types(value).items():
                self.write_ubyte(child_type)
                self.write_string(key)
                self.write_tag_payload(child_type, child_value)
            self.write_ubyte(TAG_END)
        elif tag_type == TAG_INT_ARRAY:
            self.write_uint(len(value))
            for v in value:
                self.write_int(v)
        elif tag_type == TAG_LONG_ARRAY:
            self.write_uint(len(value))
            for v in value:
                self.write_long(v)
        else:
            raise ValueError(f"Unknown NBT tag type: {tag_type}")

    def write_root(self, root_data):
        for name, (tag_type, value) in _infer_compound_types(root_data).items():
            self.write_ubyte(tag_type)
            self.write_string(name)
            self.write_tag_payload(tag_type, value)
            break


def get_nbt_type(value):
    if isinstance(value, bool):
        return TAG_BYTE
    elif isinstance(value, int):
        if -128 <= value <= 127:
            return TAG_BYTE
        elif -32768 <= value <= 32767:
            return TAG_SHORT
        elif -2147483648 <= value <= 2147483647:
            return TAG_INT
        else:
            return TAG_LONG
    elif isinstance(value, float):
        return TAG_DOUBLE
    elif isinstance(value, str):
        return TAG_STRING
    elif isinstance(value, dict):
        return TAG_COMPOUND
    elif isinstance(value, (list, tuple)):
        return TAG_LIST
    elif isinstance(value, bytes):
        return TAG_BYTE_ARRAY
    else:
        raise ValueError(f"Cannot infer NBT type for: {type(value)}")


def _infer_list_element_type(first_element):
    if isinstance(first_element, dict):
        return TAG_COMPOUND
    if isinstance(first_element, (list, tuple)):
        return TAG_LIST
    return get_nbt_type(first_element)


def _infer_compound_types(compound):
    result = {}
    for key, value in compound.items():
        tag_type = get_nbt_type(value)
        if isinstance(value, int) and not isinstance(value, bool):
            if value > 2147483647 or value < -2147483648:
                tag_type = TAG_LONG
            elif value > 32767 or value < -32768:
                tag_type = TAG_INT
        result[key] = (tag_type, value)
    return result


def parse(data):
    reader = NBTReader(data)
    return reader.read_root()


def write(root_dict):
    writer = NBTWriter()
    writer.write_root(root_dict)
    return bytes(writer.buffer)
