package instruction;

public final class InstructionInfo {

	private final byte opcode;
	private final int instructionFormat, opCount;

	public InstructionInfo(byte opcode, int instructionFormat, int argCount) {
		this.opcode = opcode;
		this.instructionFormat = instructionFormat;
		this.opCount = argCount;
	}

	public byte getOpcode() {
		return opcode;
	}

	public int getInstructionFormat() {
		return instructionFormat;
	}

	public int getArgCount() {
		return opCount;
	}

	public int getInstructionLength() {
		return getInstructionLength(instructionFormat);
	}

	public static int getInstructionLength(int instructionFormat) {
		switch (instructionFormat) {
		case 1:
			return 8;
		case 2:
			return 16;
		case 3:
			return 24;
		case 4:
			return 32;
		default:
			throw new IllegalArgumentException("Instruction format out of bounds");
		}
	}

	public String toString() {
		return String.format("InstructionInfo(op=%d, format=%d, arity=%d)", opcode,
				instructionFormat, opCount);
	}

}
