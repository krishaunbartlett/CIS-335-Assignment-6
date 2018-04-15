package assembler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import instruction.InstructionInfo;
import parse.SicLine;

public class Assembler {

	private static StringBuilder buffer;

	public static Pass1Info pass1(List<String> lines, Map<String, InstructionInfo> optab) {
		Map<String, Integer> symtab = new HashMap<>();
		List<SicLine> parsedLines = new LinkedList<>();
		String programName = "";
		int startAddr = 0;
		List<String> intermediate = new ArrayList<>();
		int locctr = startAddr;
		lineIterLoop: for (String line : lines) {
			// skip empty lines, whitespace lines and comment lines
			if (line.trim().isEmpty() || line.matches("^\\w+\\..+$"))
				continue;
			SicLine parsedLine = SicLine.parseLine(line, locctr);
			parsedLines.add(parsedLine);
			String label = parsedLine.getLabel();
			int lineLoc = locctr;
			String instruction = parsedLine.getInstruction();

			// Update SYMTAB if label is present
			if (label != null && !label.isEmpty() && !instruction.equals("START"))
				if (symtab.containsKey(label))
					throw new IllegalStateException("Multiple occurrences of label: " + label);
				else
					symtab.put(label, locctr);

			switch (instruction.toUpperCase()) {
			case "BASE":
				break;
			case "NOBASE":
				break;
			case "START":
				programName = parsedLine.getLabel();
				startAddr = Integer.parseInt(parsedLine.getOperands()[0]);
				break;
			case "END":
				// pass 1 finished when END is read
				intermediate.add(String.format("%06X\t%s", lineLoc, line));
				break lineIterLoop;
			case "RESW":
				// If RESW, add 3 * operand to LOCCTR
				locctr += 3 * Integer.parseInt(parsedLine.getOperands()[0]);
				break;
			case "RESB":
				// If RESB, add operand to LOCCTR
				locctr += Integer.parseInt(parsedLine.getOperands()[0]);
				break;
			case "WORD":
				locctr += 3;
				break;
			case "BYTE":
				// If BYTE, add operand length in bytes to LOCCTR
				String op = parsedLine.getOperands()[0];
				// If a sequence of chars, add one byte for each char
				if (op.matches("C'(.+)'")) {
					Matcher mat = Pattern.compile("C'(.+?)'").matcher(op);
					mat.find();
					String charSeq = mat.group(1);
					locctr += charSeq.length();
				} else if (op.matches("X'(.+?)'")) {
					Matcher mat = Pattern.compile("X'(.+?)'").matcher(op);
					mat.find();
					String charSeq = mat.group(1);
					locctr += 2 * charSeq.length();
				}
				break;
			default:
				// TODO: Can object codes have varying lengths?
				// for all other instructions, LOCCTR moves by the same
				// number of bytes as their format
				locctr += parsedLine.getInstructionFormat(optab);
				break;
			}
			// add hex code + line to listing lines
			intermediate.add(String.format("%06X\t%s", lineLoc, line));
		}

		// calculate the program length using last LOCCTR value
		int programLength = locctr - startAddr;
		return Pass1Info.of(intermediate, parsedLines, symtab, programName, startAddr,
				programLength);
	}

	public static List<String> pass2(Pass1Info pass1Info, Map<String, InstructionInfo> optab) {

		// extract relevant info from the results of pass 1
		List<SicLine> parsedLines = pass1Info.getParsedLines();
		Map<String, Integer> symtab = pass1Info.getSymtab();
		String programName = pass1Info.getProgramName();
		int startAddr = pass1Info.getStartAddr();
		int programLength = pass1Info.getProgramLength();

		// create buffer for lines of object code
		List<String> code = new ArrayList<>();

		String hexStartAddr = String.format("%06X", startAddr);

		// write heading line
		buffer = new StringBuilder(20);
		buffer.append("H");
		buffer.append(String.format("%-6s", programName));
		buffer.append(hexStartAddr);
		buffer.append(String.format("%06X", programLength));
		code.add(buffer.toString());

		// variables for tracking state and accumulating output
		// throughout the pass
		boolean base = false;
		int baseAddr = 0;
		int memoryLoc = startAddr;

		buffer = new StringBuilder(70);
		buffer.append("T##" + hexStartAddr);

		lineIterLoop: for (SicLine line : parsedLines) {
			String instruction = line.getInstruction();
			String[] ops = line.getOperands();
			String op = ops != null && ops.length > 0 ? ops[0] : null;
			switch (instruction.toUpperCase()) {
			case "START":
				continue lineIterLoop;
			case "END":
				break lineIterLoop;
			case "BASE":
				base = true;
				if (line.getOperands().length != 1)
					throw new IllegalStateException("Invalid number of operands on line "
							+ line.getMemLoc() + ", expected 1");
				String lbl = line.getOperands()[0];
				Integer parsedAddr = symtab.get(lbl);
				if (parsedAddr == null)
					throw new IllegalStateException("Label not found: " + lbl);
				baseAddr = parsedAddr.intValue();
				break;
			case "NOBASE":
				base = false;
				break;
			case "RESW":
				if (!op.matches("\\d+"))
					throw new IllegalStateException("Expected number after RESW");
				int words = Integer.parseInt(op);
				for (int i = 0; i < words; i++)
					memoryLoc = writeToBuffer(code, "000000", memoryLoc);
				break;
			case "RESB":
				// write 2 hex digits per byte
				if (!op.matches("\\d+"))
					throw new IllegalStateException("Expected number after RESB");
				int bytes = Integer.parseInt(op);
				for (int i = 0; i < bytes; i++)
					memoryLoc = writeToBuffer(code, "00", memoryLoc);
				break;
			case "WORD":
				int val = Integer.parseInt(op);
				String hex = String.format("%06X", val);
				memoryLoc = writeToBuffer(code, hex, memoryLoc);
				break;
			case "BYTE":
				// TODO: Handle BYTE using C'', X'' in pass 2
				if (op.matches("C'(.+)'")) {

				} else if (op.matches("X'(.+)'")) {

				}
				break;
			default:
				String s = line.getObjectCode(optab, symtab, base, baseAddr);
				memoryLoc = writeToBuffer(code, s, memoryLoc);
				break;
			}
		}
		// write last line to record
		int bytes = (buffer.length() - 9) / 2;
		String newBuf = buffer.toString().replaceFirst("##", String.format("%02X", bytes));
		code.add(newBuf);

		// write end record
		buffer = new StringBuilder(7);
		buffer.append("E");
		buffer.append(hexStartAddr);
		code.add(buffer.toString());
		return code;
	}

	private static int writeToBuffer(List<String> code, String s, int memloc) {
		if (buffer.length() + s.length() > buffer.capacity()) {
			int bytes = (buffer.length() - 9) / 2;
			String newBuf = buffer.toString().replaceFirst("##", String.format("%02X", bytes));
			code.add(newBuf);
			buffer = new StringBuilder(70);
			buffer.append(String.format("T##%06X%s", memloc, s));
			memloc += s.length() / 2;
		} else {
			buffer.append(s);
			int bytes = s.length() / 2;
			memloc += bytes;
		}
		return memloc;
	}

}
