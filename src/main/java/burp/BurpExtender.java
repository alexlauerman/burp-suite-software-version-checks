package burp;

import com.codemagi.burp.PassiveScan;
import com.codemagi.burp.ScanIssue;
import com.codemagi.burp.ScanIssueConfidence;
import com.codemagi.burp.ScanIssueSeverity;
import com.codemagi.burp.ScannerMatch;
import com.monikamorrow.burp.BurpSuiteTab;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Burp Extender to find instances of applications revealing software version numbers
 *
 * Some examples:
 * <li>Apache Tomcat/6.0.24 - Error report
 * <li>Server: Apache/2.2.4 (Unix) mod_perl/2.0.3 Perl/v5.8.8
 * <li>X-AspNet-Version: 4.0.30319
 *
 * @author August Detlefsen [augustd at codemagi dot com]
 * @contributor Thomas Dosedel [thom at secureideas dot com] for match rules
 */
public class BurpExtender extends PassiveScan {

    public static final String TAB_NAME = "Versions";
    public static final String EXTENSION_NAME = "Software Version Checks";

    protected RuleTableComponent rulesTable;
    protected BurpSuiteTab mTab;
	
	protected Map<String,Set<String>> versions = new HashMap<>();

    @Override
    protected void initPassiveScan() {
        //set the extension Name		 
 	extensionName = EXTENSION_NAME;
        
        //set the settings namespace
        settingsNamespace = "SVC_";

        rulesTable = new RuleTableComponent(this, callbacks);

        mTab = new BurpSuiteTab(TAB_NAME, callbacks);
        mTab.addComponent(rulesTable);
    }

//    ::TODO:: Add so that settings can save on exit
//    @Override
//    public void extensionUnloaded() {
//        mTab.saveSettings();
//    }
	
	/**
	 * Overridden to better consolidate duplicates
	 * 
	 * @param matches
	 * @param baseRequestResponse
	 * @return The consolidated list of issues found
	 */
	@Override
	protected List<IScanIssue> processIssues(List<ScannerMatch> matches, IHttpRequestResponse baseRequestResponse) {
		List<IScanIssue> issues = new ArrayList<>();
		if (!matches.isEmpty()) {
			//get the domain
			URL url = helpers.analyzeRequest(baseRequestResponse).getUrl();
			String domain = url.getHost();
			callbacks.printOutput("Processing issues for: " + domain);
			
			//get the existing matches for this domain
			Set<String> domainMatches = versions.get(domain);
			if (domainMatches == null) {
				domainMatches = new HashSet<String>();
				versions.put(domain, domainMatches);
			}
			boolean foundUnique = false;
			
			Collections.sort(matches); //matches must be in order
			//get the offsets of scanner matches
			List<int[]> startStop = new ArrayList<>(1);
			for (ScannerMatch match : matches) {
				callbacks.printOutput("Processing match: " + match);
				callbacks.printOutput("    start: " + match.getStart() + " end: " + match.getEnd() + " full match: " + match.getFullMatch() + " group: " + match.getMatchGroup());
				
				//add a marker for code highlighting
				startStop.add(new int[]{match.getStart(), match.getEnd()});
				
				//have we seen this match before? 
				if (!domainMatches.contains(match.getFullMatch())) { 
					foundUnique = true;
					callbacks.printOutput("NEW MATCH!");
				}
				domainMatches.add(match.getFullMatch());
			}
			if (foundUnique) issues.add(getScanIssue(baseRequestResponse, matches, startStop));
			callbacks.printOutput("issues: " + issues.size());
		}
		
		return issues;
	}

    protected String getIssueName() {
        return "Software Version Numbers Revealed";
    }

    protected String getIssueDetail(List<com.codemagi.burp.ScannerMatch> matches) {
	StringBuilder description = new StringBuilder(matches.size() * 256);
	description.append("The server software versions used by the application are revealed by the web server.<br>");
	description.append("Displaying version information of software information could allow an attacker to determine which vulnerabilities are present in the software, particularly if an outdated software version is in use with published vulnerabilities.<br><br>");
	description.append("The following software appears to be in use:<br><br>");

	for (ScannerMatch match : matches) {
	    //add a description
	    description.append("<li>");

	    description.append(match.getType()).append(": ").append(match.getMatchGroup());
	}

	return description.toString();
    }

    protected ScanIssueSeverity getIssueSeverity(List<com.codemagi.burp.ScannerMatch> matches) {
	ScanIssueSeverity output = ScanIssueSeverity.INFO;
	for (ScannerMatch match : matches) {
	    //if the severity value of the match is higher, then update the stdout value
	    ScanIssueSeverity matchSeverity = match.getSeverity();
	    if (matchSeverity != null &&
		output.getValue() < matchSeverity.getValue()) {

		output = matchSeverity;
	    }
	}
	return output;
    }

    protected ScanIssueConfidence getIssueConfidence(List<com.codemagi.burp.ScannerMatch> matches) {
	ScanIssueConfidence output = ScanIssueConfidence.TENTATIVE;
	for (ScannerMatch match : matches) {
	    //if the severity value of the match is higher, then update the stdout value
	    ScanIssueConfidence matchConfidence = match.getConfidence();
	    if (matchConfidence != null &&
		output.getValue() < matchConfidence.getValue()) {

		output = matchConfidence;
	    }
	}
	return output;
    }

    @Override
    protected IScanIssue getScanIssue(IHttpRequestResponse baseRequestResponse, List<ScannerMatch> matches, List<int[]> startStop) {
	ScanIssueSeverity overallSeverity = getIssueSeverity(matches);
        ScanIssueConfidence overallConfidence = getIssueConfidence(matches);

        return new ScanIssue(
		baseRequestResponse,
		helpers,
		callbacks,
		startStop,
		getIssueName(),
		getIssueDetail(matches),
		overallSeverity.getName(),
		overallConfidence.getName());
    }

}
