package parse;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import instruction.InstructionInfo;

public final class SicLine {

	private final String label, instruction;
	private final String[] operands;
	private final EnumSet<AddressingMode> modes;
	private final int memloc;

	private static final Pattern linePattern = Pattern.compile(
			"^(?<label>\\w+)?\\s+(?<inst>\\+?[a-z]+)(?:\\s+(?<operands>.+))?$",
			Pattern.CASE_INSENSITIVE);
	private static final Pattern movLoadSub = Pattern.compile("MOV\\s+%R([ABLSTX])\\s*,");
	private static final Pattern movStoreSub = Pattern.compile("MOV\\s+(.+),\\s*%R([ABLSTX])");

	private SicLine(String label, String instruction, String[] operands,
			EnumSet<AddressingMode> modes, int memloc) {
		this.label = label;
		this.instruction = instruction;
		this.operands = operands;
		this.modes = modes;
		this.memloc = memloc;
	}

	private static enum AddressingMode {
		N, I, X, B, P, E
	}

	public static SicLine fromInfo(String label, String instruction, String rest, int addr) {
		EnumSet<AddressingMode> modes = EnumSet.noneOf(AddressingMode.class);
		if (rest != null && !rest.isEmpty()) {
			// guaranteed to be at the end from the MOV replacement
			if (rest.matches(".+\\[%RX\\]")) {
				modes.add(AddressingMode.X);
				rest = rest.substring(0, rest.length() - 5);
			}
			// immediate handling
			if (rest.charAt(0) == '#') {
				modes.add(AddressingMode.I);
				rest = rest.substring(1);
			}
			// indirect handling
			if (rest.charAt(0) == '@') {
				modes.add(AddressingMode.N);
				rest = rest.substring(1);
			}
			// extended format handling
			if (instruction.charAt(0) == '+') {
				modes.add(AddressingMode.E);
				instruction = instruction.substring(1);
			}
			if (instruction.charAt(0) == '=') {
				// TODO: Implement '=' operand prefix
				instruction = instruction.substring(1);
			}
		}
		if (!modes.contains(AddressingMode.I) && !modes.contains(AddressingMode.N)) {
			modes.addAll(EnumSet.of(AddressingMode.N, AddressingMode.I));
		}
		if (rest == null)
			return new SicLine(label, instruction, new String[] {}, modes, addr);
		String[] operands = Pattern.compile(",").splitAsStream(rest).map(String::trim)
				.toArray(String[]::new);
		return new SicLine(label, instruction, operands, modes, addr);
	}

	public static SicLine parseLine(String line, int pc) {
		// substitute MOVs with real instructions
		line = movLoadSub.matcher(line).replaceAll("LD$1\t");
		line = movStoreSub.matcher(line).replaceAll("ST$2\t$1");
		Matcher m = linePattern.matcher(line);
		if (m.find()) {
			String label = m.group("label");
			String instruction = m.group("inst");
			String rest = m.group("operands");
			return SicLine.fromInfo(label, instruction, rest, pc);
		} else {
			throw new IllegalStateException("Could not parse line: " + line);
		}
	}

	// return the object code as a hexadecimal String
	public String getObjectCode(Map<String, InstructionInfo> optab, Map<String, Integer> symtab,
			boolean base, int baseAddr) {
		InstructionInfo instInfo = optab.get(instruction);
		boolean isImmediate = modes.contains(AddressingMode.I) && !modes.contains(AddressingMode.N);
		if (instInfo == null)
			throw new IllegalStateException("Instruction not found: " + instruction);
		int opCount = instInfo.getArgCount();
		if (operands.length != opCount) {
			String args = Arrays.stream(operands).collect(Collectors.joining(", "));
			throw new IllegalArgumentException("Invalid number of operands: " + args);
		}
		int format = instInfo.getInstructionFormat();

		byte b = (byte) instInfo.getOpcode();
		int res = 0;

		if (format == 1) {
			// format 1 simply the opcode
			res = b;
		} else if (format == 2) {
			// format 2 uses registers, which are stored in optab
			if (opCount == 1) {
				String op = operands[0];
				InstructionInfo register = optab.get(op);
				if (register == null)
					throw new IllegalStateException("Illegal register: " + register);
				byte addr = register.getOpcode();
				// write addr to high four bits of second byte
				// bits 9-12
				addr <<= 4;
				res |= b << 16;
				res |= addr << 8;
			} else if (opCount == 2) { // two arguments
				String op1 = operands[0];
				String op2 = operands[1];
				InstructionInfo reg1 = optab.get(op1);
				InstructionInfo reg2 = optab.get(op2);
				if (reg1 == null)
					throw new IllegalStateException("Illegal register: " + reg1);
				if (reg2 == null)
					throw new IllegalStateException("Illegal register: " + reg2);
				byte addr1 = reg1.getOpcode();
				byte addr2 = reg2.getOpcode();
				// make upper nibble the first four bits of addr1
				res |= b << 16;
				res |= addr1 << 8;
				res |= addr2 << 4;
			} else {
				throw new IllegalArgumentException("Unhandled operand count");
			}
		} else if (format == 3 || format == 4) {
			// determine addressing mode
			if (base)
				modes.add(AddressingMode.B);
			else
				modes.add(AddressingMode.P);

			// differentiate between formats 3 and 4
			if (modes.contains(AddressingMode.E))
				format = 4;

			// translate modes into bits
			// N and I share a byte with the opcode
			if (modes.contains(AddressingMode.N))
				b |= 0b10;
			if (modes.contains(AddressingMode.I))
				b |= 0b01;
			byte b2 = 0;
			if (modes.contains(AddressingMode.X))
				b2 |= 1 << 7;
			if (modes.contains(AddressingMode.B))
				b2 |= 1 << 6;
			if (modes.contains(AddressingMode.P))
				b2 |= 1 << 5;
			if (modes.contains(AddressingMode.E))
				b2 |= 1 << 4;

			// join the bytes so far into the final result
			// format 4 must be shifted over by an extra byte
			int formatOffset = format == 4 ? 8 : 0;
			res |= b << 16 + formatOffset;
			res |= b2 << 8 + formatOffset;

			// obtain address from operand
			// if immediate, parse directly, otherwise lookup in SYMTAB
			String op = operands[0];
			int addr;
			if (isImmediate && op.matches("\\d+"))
				addr = Integer.parseInt(operands[0]);
			else
				addr = findInSymtab(symtab, op);

			// PC is always a line ahead of the current instruction
			int pc = memloc + format; // TODO: (+format) or always (+3)?
			// SIC-XE never uses direct addressing
			int offset = base ? addr - baseAddr : addr - pc;
			// mask away unnecessary sign bits in the offset
			if (format == 3)
				// mask only first 12 bits
				offset = offset & 0xFFF;
			else
				// mask only first 20 bits
				offset = offset & 0xFFFFF;
			// join address offset with result
			res |= offset;
		} else {
			throw new IllegalArgumentException("Illegal format: " + format);
		}

		// format result into hexadecimal
		// note that every format's id is half of its width in bytes
		String hex = String.format("%0" + (format * 2) + "X", res);
		return hex;
	}

	private int findInSymtab(Map<String, Integer> symtab, String key) {
		Integer val = symtab.get(key);
		if (val == null)
			throw new IllegalStateException("Symbol not found: " + key);
		return val.intValue();
	}

	public String getLabel() {
		return label;
	}

	public String getInstruction() {
		return instruction;
	}

	public String[] getOperands() {
		return operands;
	}

	public EnumSet<AddressingMode> getModes() {
		return modes;
	}

	public int getMemLoc() {
		return memloc;
	}

	public int getInstructionFormat(Map<String, InstructionInfo> optab) {
		InstructionInfo instInfo = optab.get(instruction);
		if (instInfo == null)
			throw new IllegalStateException("Invalid instruction: " + instruction);
		int format = instInfo.getInstructionFormat();
		if (format == 3 && modes.contains(AddressingMode.E))
			format = 4;
		return format;
	}

	@Override
	public String toString() {
		return String.format("SicLine(loc=%s, label=%s, inst=%s, ops=%s, modes=%s)",
				Integer.toHexString(memloc), label, instruction,
				Arrays.stream(operands).collect(Collectors.joining(" | ")), modes);
	}

}