package krasa.mavenhelper.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Goals extends DomainObject {
	private List<Goal> goals = new ArrayList<Goal>();

	public Goals() {
	}

	public Goals(List<Goal> goals) {
		this.goals = goals;
	}

	public List<Goal> getGoals() {
		return goals;
	}

	public void setGoals(List<Goal> goals) {
		this.goals = goals;
	}

	public List<String> asStrings() {
		return getStrings(goals);
	}

	public boolean remove(Goal o) {
		return goals.remove(o);
	}

	public boolean add(Goal o) {
		return goals.add(o);
	}

	public List<String> getGoalsAsStrings() {
		return getStrings(goals);
	}

	private List<String> getStrings(final List<Goal> goals1) {
		List<String> strings = new ArrayList<String>();
		for (Goal goal : goals1) {
			strings.add(goal.getCommandLine());
		}
		return strings;
	}

	public void add(String s) {
		goals.add(new Goal(s));
	}

	public int size() {
		return goals.size();
	}

	public boolean remove(Object goal) {
		return goals.remove(goal);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Goals goals1)) {
			return false;
		}
		return Objects.equals(goals, goals1.goals);
	}

	@Override
	public int hashCode() {
		return Objects.hash(goals);
	}
}
