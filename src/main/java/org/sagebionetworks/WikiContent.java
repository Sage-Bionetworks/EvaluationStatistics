package org.sagebionetworks;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class WikiContent {
	private String markdown;
	private List<File> files;
	
	public WikiContent() {
		setFiles(new ArrayList<File>());
	}
	
	public String getMarkdown() {
		return markdown;
	}
	
	public void setMarkdown(String markdown) {
		this.markdown = markdown;
	}
	
	public List<File> getFiles() {
		return files;
	}
	
	public void setFiles(List<File> files) {
		this.files = files;
	}
	
	public void addFile(File file) {files.add(file);}
}
