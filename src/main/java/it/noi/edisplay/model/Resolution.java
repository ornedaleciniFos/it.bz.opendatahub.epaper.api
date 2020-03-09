package it.noi.edisplay.model;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.UUID;


/**
 * To keep the screen resolution of the displays dynamically, so that every resolution can be supported.
 * <p>
 * Will be used also for resizing the images, if they don't fit the resolution.
 */
@Entity
@Table(
	uniqueConstraints = @UniqueConstraint(columnNames = {"width", "height"}), //to prevent duplicate resolutions
	name = "resolutions"
)
public class Resolution {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Integer id;

	@NotNull
	private String uuid;

	@NotNull
	private int width;

	@NotNull
	private int height;

	public  Resolution(){

	}

	public Resolution(int width, int height) {
		this.width = width;
		this.height = height;
	}

	public int getWidth() {
		return width;
	}

	public void setWidth(int width) {
		this.width = width;
	}

	public int getHeight() {
		return height;
	}

	public void setHeight(int height) {
		this.height = height;
	}


	public String getUuid() {
		return uuid;
	}

	public void setUuid(String uuid) {
		this.uuid = uuid;
	}

	@PrePersist
	public void prePersist() {
		this.setUuid(UUID.randomUUID().toString());
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}
}