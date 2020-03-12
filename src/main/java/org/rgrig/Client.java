package org.rgrig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Problems {
    public String good;
    public String bad;
    public String solution;
    public ArrayList<String> bads;
    public String name;
    public String statement;

    public Problems(String good, String bad, String name, String statement) {
        this.good = good;
        this.bad = bad;
        this.name = name;
        this.bads = new ArrayList<>();
        this.statement = statement;
    }

    @Override
    public String toString() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        try {
            return objectMapper.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            return "error";
        }
    }
}

class Client extends WebSocketClient {
    enum State {
        START, IN_PROGRESS,
    }

    State state = State.START;

    String repoName = null;
    Map<String, List<String>> parents;
    String[] commits;
    Map<String, Boolean> good;
    int next;

    String kentId;
    String token;

    ArrayList<Problems> problems;

    int instance = 0;
    JSONObject jsonRepo;
    String repo_data = "";
    private boolean debug;

    Client(final URI server, final String kentId, final String token) {
        super(server);
        this.kentId = kentId;
        this.token = token;
        this.debug = false;
        this.problems = new ArrayList<>();
        setConnectionLostTimeout(0);
    }

    @Override
    public void onMessage(final String messageText) {
        final JSONObject message = new JSONObject(messageText);
        switch (state) {
            case START:
                if (message.has("Repo")) {
                    repo_data = messageText;
                    // Make an array with all commits. Also, remember the repo dag.
                    jsonRepo = message.getJSONObject("Repo");
                    repoName = jsonRepo.getString("name");
                    JSONArray jsonDag = jsonRepo.getJSONArray("dag");
                    commits = new String[jsonDag.length()];
                    parents = new HashMap<>();
                    for (int i = 0; i < jsonDag.length(); ++i) {
                        JSONArray entry = jsonDag.getJSONArray(i);
                        commits[i] = entry.getString(0);
                        JSONArray iParents = entry.getJSONArray(1);
                        List<String> ps = new ArrayList<>();
                        for (int j = 0; j < iParents.length(); ++j) {
                            ps.add(iParents.getString(j));
                        }
                        parents.put(commits[i], ps);
                    }
                    instance = 0;
                    assert commits.length >= 2;
                } else if (message.has("Instance")) {
                    instance += 1;
                    if (repoName == null) {
                        System.err.println("Protocol error: instace without having seen a repo.");
                        close();
                    }
                    JSONObject jsonInstance = message.getJSONObject("Instance");
                    String knownGood = jsonInstance.getString("good");
                    String knownBad = jsonInstance.getString("bad");
                    if (repoName.contains("tiny")) {
                        problems.add(new Problems(knownGood, knownBad, repoName + "-" + instance, repo_data));
                        System.out.printf("(good %s; bad %s) of %s\n", knownGood, knownBad, repoName + instance);
                    }
                    if (commits.length > 30) {
                        send("\"GiveUp\"");
                        return;
                    }
                    state = State.IN_PROGRESS;
                    next = 0;
                    good = new HashMap<>();
                    ask();
                } else if (message.has("Score")) {
                    System.out.println("score:");
                    JSONObject scores = message.getJSONObject("Score");
                    for (String s : scores.keySet()) {
                        if (scores.get(s).getClass().equals(org.json.JSONObject.class)){
                            for (Problems problem : problems) {
                                if (s.equals(problem.name)) {
                                    System.out.println(problem);
                                }
                            }
                        }
                    }

                    close();
                } else {
                    System.err.println("Unexpected message while waiting for a problem.");
                    close();
                }
                break;
            case IN_PROGRESS:
                if (message.has("Answer")) {
                    if (repoName.contains("tiny") && next > 0) {
                        if (!"Good".equals(message.get("Answer"))) {
                            System.out.println("adding bad commit");
                            problems.get(problems.size() - 1).bads.add(commits[next - 1]);
                        }
                    }
                    good.put(commits[next++], "Good".equals(message.get("Answer")));
                    if (next == commits.length) {
                        for (String commit : commits) {
                            boolean allParentsGood = true;
                            for (String p : parents.get(commit)) {
                                allParentsGood &= good.get(p);
                            }
                            if (!good.get(commit) && allParentsGood) {
                                state = State.START;
                                if (repoName.contains("tiny")) {
                                    problems.get(problems.size() - 1).solution = commit;
                                    System.out.println(String.format("solution: %s", commit));
                                }

                                send(new JSONObject().put("Solution", commit).toString());
                                return;
                            }
                        }
                        assert false; // No BUG?
                    } else {
                        ask();
                    }
                } else {
                    System.err.println("Unexpected message while in-progress.");
                    close();
                }
                break;
            default:
                assert false;
        }

    }

    void ask() {
        send(new JSONObject().put("Question", commits[next]).toString());
    }

    @Override
    public void onClose(final int arg0, final String arg1, final boolean arg2) {
        System.out.printf("L: onClose(%d, %s, %b)\n", arg0, arg1, arg2);
    }

    @Override
    public void onError(final Exception arg0) {
        System.out.printf("L: onError(%s)\n", arg0);
    }


    @Override
    public void send(String text) {
        super.send(text);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        JSONArray authorization = new JSONArray(new Object[]{kentId, token});
        send(new JSONObject().put("User", authorization).toString());
    }

    public static void main(String[] args) {
        System.out.println(new Problems("a", "b", "ugly", "cat"));
        String msg = "{\"Score\":{\"small-jsonview-0\":\"GaveUp\",\"small-jsonview-1\":\"GaveUp\",\"tiny-complete-8\":{\"Correct\":26},\"tiny-complete-7\":{\"Correct\":26},\"tiny-complete-9\":{\"Correct\":26},\"big-gcc-1\":\"GaveUp\",\"big-gcc-0\":\"GaveUp\",\"tiny-complete-0\":{\"Correct\":26},\"small-thesilversearcher-0\":\"GaveUp\",\"tiny-complete-2\":{\"Correct\":26},\"tiny-complete-1\":{\"Correct\":26},\"tiny-complete-4\":{\"Correct\":26},\"tiny-complete-3\":{\"Correct\":26},\"tiny-complete-6\":{\"Correct\":26},\"big-mycroft-core-0\":\"GaveUp\",\"big-mycroft-core-1\":\"GaveUp\",\"tiny-complete-5\":{\"Correct\":26},\"small-latex-0\":\"GaveUp\",\"small-lime-0\":\"GaveUp\",\"small-dosboxx-0\":\"GaveUp\",\"small-depot-0\":\"GaveUp\",\"small-normalize-0\":\"GaveUp\",\"small-normalize-1\":\"GaveUp\",\"big-chromium-0\":\"GaveUp\",\"big-chromium-1\":\"GaveUp\",\"small-texlive-0\":\"GaveUp\",\"small-caret-0\":\"GaveUp\",\"medium-gnome-shell-0\":\"GaveUp\",\"small-960-0\":\"GaveUp\",\"small-960-1\":\"GaveUp\",\"small-textmate-0\":\"GaveUp\",\"medium-bootstrap-0\":\"GaveUp\",\"small-ohmyzsh-0\":\"GaveUp\",\"small-animate-0\":\"GaveUp\",\"small-animate-1\":\"GaveUp\",\"medium-ghc-0\":\"GaveUp\",\"medium-gimp-0\":\"GaveUp\",\"small-KerbalMultiPlayer-0\":\"GaveUp\",\"tiny-chain-0\":{\"Correct\":26},\"medium-neovim-0\":\"GaveUp\",\"tiny-chain-2\":{\"Correct\":26},\"tiny-chain-1\":{\"Correct\":26},\"tiny-chain-4\":{\"Correct\":26},\"tiny-chain-3\":{\"Correct\":26},\"medium-chef-0\":\"GaveUp\",\"tiny-chain-6\":{\"Correct\":26},\"tiny-chain-5\":{\"Correct\":26},\"tiny-chain-8\":{\"Correct\":26},\"tiny-chain-7\":{\"Correct\":26},\"small-kdevplatform-0\":\"GaveUp\",\"medium-brackets-0\":\"GaveUp\",\"big-linux-0\":\"GaveUp\",\"big-linux-1\":\"GaveUp\",\"medium-vscode-0\":\"GaveUp\",\"medium-nautilus-0\":\"GaveUp\",\"medium-git-0\":\"GaveUp\",\"tiny-chain-9\":{\"Correct\":26},\"small-slap-0\":\"GaveUp\",\"tiny-gitsweep-7\":{\"Correct\":18},\"tiny-gitsweep-6\":{\"Correct\":18},\"tiny-gitsweep-9\":{\"Correct\":18},\"tiny-gitsweep-8\":{\"Correct\":18},\"medium-puppet-0\":\"GaveUp\",\"tiny-gitsweep-3\":{\"Correct\":18},\"tiny-gitsweep-2\":{\"Correct\":18},\"tiny-gitsweep-5\":{\"Correct\":18},\"tiny-gitsweep-4\":{\"Correct\":18},\"medium-gtk-0\":\"GaveUp\",\"tiny-gitsweep-1\":{\"Correct\":18},\"tiny-gitsweep-0\":{\"Correct\":18},\"big-salt-1\":\"GaveUp\",\"big-salt-0\":\"GaveUp\",\"tiny-diamonds-9\":{\"Correct\":26},\"medium-ansible-0\":\"GaveUp\",\"tiny-diamonds-6\":{\"Correct\":26},\"tiny-diamonds-5\":{\"Correct\":26},\"tiny-diamonds-8\":{\"Correct\":26},\"small-overleaf-0\":\"GaveUp\",\"tiny-diamonds-7\":{\"Correct\":26},\"small-overleaf-1\":\"GaveUp\",\"tiny-diamonds-2\":{\"Correct\":26},\"big-firefox-0\":\"GaveUp\",\"tiny-diamonds-1\":{\"Correct\":26},\"tiny-diamonds-4\":{\"Correct\":26},\"tiny-diamonds-3\":{\"Correct\":26},\"tiny-random-8\":{\"Correct\":26},\"tiny-random-7\":{\"Correct\":26},\"tiny-random-6\":{\"Correct\":26},\"tiny-diamonds-0\":{\"Correct\":26},\"big-firefox-1\":\"GaveUp\",\"tiny-random-5\":{\"Correct\":26},\"tiny-random-9\":{\"Correct\":26},\"tiny-random-0\":{\"Correct\":26},\"big-g0v-1\":\"GaveUp\",\"tiny-random-4\":{\"Correct\":26},\"tiny-random-3\":{\"Correct\":26},\"tiny-random-2\":{\"Correct\":26},\"tiny-random-1\":{\"Correct\":26},\"big-g0v-0\":\"GaveUp\"}}";
        JSONObject message = new JSONObject(msg);
        System.out.println(message);
        System.out.println(message.has("Score"));
        System.out.println(message.getJSONObject("Score"));
        JSONObject scores = message.getJSONObject("Score");
        for (String s : scores.keySet()) {
            if (scores.get(s).getClass().equals(org.json.JSONObject.class)){
                System.out.println(s);
            }
        }
    }
}