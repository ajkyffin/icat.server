package org.icatproject.core.entity;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.json.stream.JsonGenerator;
import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.FetchType;
import javax.persistence.Index;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import javax.persistence.UniqueConstraint;
import javax.xml.bind.annotation.XmlRootElement;

import org.icatproject.core.IcatException;
import org.icatproject.core.manager.EntityBeanManager.PersistMode;
import org.icatproject.core.manager.GateKeeper;
import org.icatproject.core.manager.LuceneApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Comment("A data file")
@SuppressWarnings("serial")
@Entity
@XmlRootElement
@Table(uniqueConstraints = { @UniqueConstraint(columnNames = { "DATASET_ID", "NAME" }) }, indexes = {
		@Index(columnList = "location") })
public class Datafile extends EntityBaseBean implements Serializable {

	private final static Logger logger = LoggerFactory.getLogger(Datafile.class);

	@Comment("Checksum of file represented as a string")
	private String checksum;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "datafile")
	private List<DataCollectionDatafile> dataCollectionDatafiles = new ArrayList<DataCollectionDatafile>();

	@Comment("Date of creation of the actual file rather than storing the metadata")
	@Temporal(TemporalType.TIMESTAMP)
	private Date datafileCreateTime;

	@ManyToOne(fetch = FetchType.LAZY)
	private DatafileFormat datafileFormat;

	@Comment("Date of modification of the actual file rather than of the metadata")
	@Temporal(TemporalType.TIMESTAMP)
	private Date datafileModTime;

	@Comment("The dataset which holds this file")
	@JoinColumn(name = "DATASET_ID", nullable = false)
	@ManyToOne(fetch = FetchType.LAZY)
	private Dataset dataset;

	@Comment("A full description of the file contents")
	private String description;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "destDatafile")
	private List<RelatedDatafile> destDatafiles = new ArrayList<RelatedDatafile>();

	@Comment("The Digital Object Identifier associated with this data file")
	private String doi;

	@Comment("Expressed in bytes")
	private Long fileSize;

	@Comment("The logical location of the file - which may also be the physical location")
	private String location;

	@Comment("A name given to the file")
	@Column(name = "NAME", nullable = false)
	private String name;

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "datafile")
	private List<DatafileParameter> parameters = new ArrayList<DatafileParameter>();

	@OneToMany(cascade = CascadeType.ALL, mappedBy = "sourceDatafile")
	private List<RelatedDatafile> sourceDatafiles = new ArrayList<RelatedDatafile>();

	/* Needed for JPA */
	public Datafile() {
	}

	public String getChecksum() {
		return checksum;
	}

	public List<DataCollectionDatafile> getDataCollectionDatafiles() {
		return dataCollectionDatafiles;
	}

	public Date getDatafileCreateTime() {
		return datafileCreateTime;
	}

	public DatafileFormat getDatafileFormat() {
		return datafileFormat;
	}

	public Date getDatafileModTime() {
		return datafileModTime;
	}

	public Dataset getDataset() {
		return dataset;
	}

	public String getDescription() {
		return description;
	}

	public List<RelatedDatafile> getDestDatafiles() {
		return destDatafiles;
	}

	public String getDoi() {
		return doi;
	}

	public Long getFileSize() {
		return fileSize;
	}

	public String getLocation() {
		return location;
	}

	public String getName() {
		return name;
	}

	public List<DatafileParameter> getParameters() {
		return parameters;
	}

	public List<RelatedDatafile> getSourceDatafiles() {
		return sourceDatafiles;
	}

	public void setChecksum(String checksum) {
		this.checksum = checksum;
	}

	public void setDataCollectionDatafiles(List<DataCollectionDatafile> dataCollectionDatafiles) {
		this.dataCollectionDatafiles = dataCollectionDatafiles;
	}

	public void setDatafileCreateTime(Date datafileCreateTime) {
		this.datafileCreateTime = datafileCreateTime;
	}

	public void setDatafileFormat(DatafileFormat datafileFormat) {
		this.datafileFormat = datafileFormat;
	}

	public void setDatafileModTime(Date datafileModTime) {
		this.datafileModTime = datafileModTime;
	}

	public void setDataset(Dataset dataset) {
		this.dataset = dataset;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setDestDatafiles(List<RelatedDatafile> destDatafiles) {
		this.destDatafiles = destDatafiles;
	}

	public void setDoi(String doi) {
		this.doi = doi;
	}

	public void setFileSize(Long fileSize) {
		this.fileSize = fileSize;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setParameters(List<DatafileParameter> parameters) {
		this.parameters = parameters;
	}

	public void setSourceDatafiles(List<RelatedDatafile> sourceDatafiles) {
		this.sourceDatafiles = sourceDatafiles;
	}

	@Override
	public void getDoc(JsonGenerator gen) {
		StringBuilder sb = new StringBuilder(name);
		if (description != null) {
			sb.append(" " + description);
		}
		if (doi != null) {
			sb.append(" " + doi);
		}
		if (datafileFormat != null) {
			sb.append(" " + datafileFormat.getName());
		}
		LuceneApi.encodeTextfield(gen, "text", sb.toString());
		if (datafileModTime != null) {
			LuceneApi.encodeStringField(gen, "date", datafileModTime);
		} else if (datafileCreateTime != null) {
			LuceneApi.encodeStringField(gen, "date", datafileCreateTime);
		} else {
			LuceneApi.encodeStringField(gen, "date", modTime);
		}
		LuceneApi.encodeStoredId(gen, id);
		LuceneApi.encodeStringField(gen, "dataset", dataset.id);
	}

	@Override
	public void preparePersist(String modId, EntityManager manager, GateKeeper gateKeeper, PersistMode persistMode)
			throws IcatException {
		super.preparePersist(modId, manager, gateKeeper, persistMode);

		if (location != null && manager.isJoinedToTransaction()) {

			try {
				// String key = propertyHandler.getKey();
				String key = "wombat";
				String loc = location;
				if (key != null) {
					int i = loc.lastIndexOf(' ');
					if (i >= 0) {
						loc = loc.substring(0, i);
					}
				}
				Path path = null;
				for (java.nio.file.Path name : (new File(loc)).toPath()) {
					List<Path> roots = null;
					// At least with MySQL it seems to be necessary to treat
					// null as a special case
					if (path == null) {
						roots = manager.createQuery("SELECT p FROM Path p where p.parent is null and p.name = :name",
								Path.class).setParameter("name", name.toString()).getResultList();
					} else {
						roots = manager
								.createQuery("SELECT p FROM Path p where p.parent =:parent and p.name = :name",
										Path.class)
								.setParameter("parent", path).setParameter("name", name.toString()).getResultList();
					}
					if (roots.size() == 0) {
						path = new Path(path, name);
						manager.persist(path);
					} else {
						path = roots.get(0);
					}
				}
				path.setFile(true);

			} catch (Exception e) {
				logger.error(e.getClass() + " " + e.getMessage());
			}

		}
	}
}
