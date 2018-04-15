package assembler;

import java.util.List;
import java.util.Map;

import parse.SicLine;

public class Pass1Info {

	private final List<String> listingLines;
	private final List<SicLine> parsedLines;
	private final Map<String, Integer> symtab;
	private final String programName;
	private final int startAddr, programLength;

	private Pass1Info(List<String> listingLines, List<SicLine> parsedLines,
			Map<String, Integer> symtab, String programName, int startAddr, int programLength) {
		this.listingLines = listingLines;
		this.parsedLines = parsedLines;
		this.symtab = symtab;
		this.programName = programName;
		this.startAddr = startAddr;
		this.programLength = programLength;
	}

	public static Pass1Info of(List<String> listingLines, List<SicLine> parsedLines,
			Map<String, Integer> symtab, String programName, int startAddr, int programLength) {
		return new Pass1Info(listingLines, parsedLines, symtab, programName, startAddr,
				programLength);
	}

	public List<String> getListingLines() {
		return listingLines;
	}

	public List<SicLine> getParsedLines() {
		return parsedLines;
	}

	public Map<String, Integer> getSymtab() {
		return symtab;
	}

	public String getProgramName() {
		return programName;
	}

	public int getStartAddr() {
		return startAddr;
	}

	public int getProgramLength() {
		return programLength;
	}

}
