import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import assembler.Assembler;
import assembler.Pass1Info;
import instruction.InstructionInfo;
import parse.SicLine;

public class Main {

	public static void main(String[] args) {
		// TODO: Remove overridden input file
		args = new String[] { "Assembler",
				"C:\\Users\\nprin\\java-workspace\\Assignment 6\\src\\main.asm" };

		final Map<String, InstructionInfo> optab = parseOptab();

		// read file
		if (args.length < 1)
			throw new IllegalArgumentException("Not enough arguments given");
		String filename = args[1];
		Path filepath = Paths.get(filename);
		if (Files.notExists(filepath))
			throw new IllegalArgumentException("File does not exist");
		String objFileName = filename.replace(".asm", ".obj"),
				lstFileName = filename.replace(".asm", ".lst");
		try {
			List<String> lines = Files.readAllLines(filepath);
			// parses lines, stores in parsedLines
			// adds line number to the beginning of each of the lines
			// updates symtab
			// return calculated program length
			Pass1Info pass1 = Assembler.pass1(lines, optab);
			List<SicLine> parsedLines = pass1.getParsedLines();

			// Verify instructions for consistency with optab
			for (SicLine sl : parsedLines) {
				String inst = sl.getInstruction();
				if (inst.equals("BASE") || inst.equals("NOBASE") || inst.equals("START")
						|| inst.equals("END"))
					continue;
				if (inst.equals("BYTE") || inst.equals("RESB") || inst.equals("WORD")
						|| inst.equals("RESW"))
					continue;
				int numOps = sl.getOperands().length;
				InstructionInfo instInfo = optab.get(inst);
				if (instInfo == null)
					throw new IllegalStateException("Invalid instruction: " + inst);
				int instOps = instInfo.getArgCount();
				if (numOps != instOps)
					throw new IllegalStateException(String.format(
							"Invalid number of operands for instruction: %s. Found %d, expected %d",
							inst, numOps, instOps));
			}

			// generates object code lines
			// appends object code to each line to make listing line
			List<String> objectCode = Assembler.pass2(pass1, optab);

			System.out.println("Lines:");
			lines.forEach(System.out::println);
			System.out.println("\nListing lines:");
			pass1.getListingLines().forEach(System.out::println);
			;
			System.out.println("\nParsed lines:");
			parsedLines.forEach(System.out::println);
			System.out.println("\nOptab:");
			optab.forEach((k, v) -> System.out.println(k + "\t=\t" + v));
			System.out.println("\nSymtab:");
			pass1.getSymtab().forEach((k, v) -> System.out.println(k + "\t=\t" + v));
			System.out.println("\nObject Code:");
			objectCode.forEach(System.out::println);
			System.out.println("\nStart address: " + pass1.getStartAddr());

			// write to listing file
			Files.write(Paths.get(lstFileName), pass1.getListingLines());
			// write to object file
			Files.write(Paths.get(objFileName), objectCode);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static Map<String, InstructionInfo> parseOptab() {
		// OPNAME, OPCODE, FORMAT, OPCOUNT
		try {
			return Collections.unmodifiableMap(Files
					.lines(Paths
							.get("C:\\Users\\nprin\\java-workspace\\Assignment 6\\src\\optab.txt"))
					.filter(l -> !l.isEmpty()).filter(l -> !l.startsWith("//"))
					.map(l -> l.split(","))
					.map(arr -> Arrays.stream(arr).map(String::trim).toArray(String[]::new))
					.collect(Collectors.toMap(arr -> arr[0], arr -> {
						byte opcode = (byte) Integer.parseInt(arr[1], 16);
						int format = Integer.parseInt(arr[2]);
						int count = Integer.parseInt(arr[3]);
						InstructionInfo info = new InstructionInfo(opcode, format, count);
						return info;
					})));
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

}
