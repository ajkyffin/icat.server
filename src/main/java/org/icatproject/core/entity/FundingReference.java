package org.icatproject.core.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

@Comment("Information about financial support")
@SuppressWarnings("serial")
@Entity
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = { "FUNDERNAME", "AWARDNUMBER" }) })
public class FundingReference extends EntityBaseBean implements Serializable {

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "funding")
	private List<InvestigationFunding> investigations = new ArrayList<>();

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "funding")
	private List<DataPublicationFunding> publications = new ArrayList<>();

	@Column(name = "FUNDERNAME", nullable = false)
	private String funderName;

	private String funderIdentifier;

	@Column(name = "AWARDNUMBER", nullable = false)
	private String awardNumber;

	private String awardTitle;

	/* Needed for JPA */
	public FundingReference() {
	}

	public List<InvestigationFunding> getInvestigations() {
		return investigations;
	}

	public List<DataPublicationFunding> getPublications() {
		return publications;
	}

	public String getFunderName() {
		return funderName;
	}

	public String getFunderIdentifier() {
		return funderIdentifier;
	}

	public String getAwardNumber() {
		return awardNumber;
	}

	public String getAwardTitle() {
		return awardTitle;
	}

	public void setInvestigations(List<InvestigationFunding> investigations) {
		this.investigations = investigations;
	}

	public void setPublications(List<DataPublicationFunding> publications) {
		this.publications = publications;
	}

	public void setFunderName(String funderName) {
		this.funderName = funderName;
	}

	public void setFunderIdentifier(String funderIdentifier) {
		this.funderIdentifier = funderIdentifier;
	}

	public void setAwardNumber(String awardNumber) {
		this.awardNumber = awardNumber;
	}

	public void setAwardTitle(String awardTitle) {
		this.awardTitle = awardTitle;
	}
}
