package com.pmease.gitplex.web.page.depot.file;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.wicket.Component;
import org.apache.wicket.extensions.ajax.markup.html.AjaxLazyLoadPanel;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.Fragment;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LoadableDetachableModel;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import com.google.common.collect.Lists;
import com.pmease.commons.git.BlobIdent;
import com.pmease.commons.git.NameAndEmail;
import com.pmease.commons.git.command.RevListCommand;
import com.pmease.commons.wicket.component.DropdownLink;
import com.pmease.commons.wicket.component.loadingindicator.LoadingIndicator;
import com.pmease.gitplex.core.entity.Depot;
import com.pmease.gitplex.web.component.avatar.Avatar;
import com.pmease.gitplex.web.component.avatar.AvatarLink;
import com.pmease.gitplex.web.component.contributorpanel.ContributorPanel;
import com.pmease.gitplex.web.page.depot.commit.CommitDetailPage;
import com.pmease.gitplex.web.page.depot.commit.DepotCommitsPage;
import com.pmease.gitplex.web.util.DateUtils;

@SuppressWarnings("serial")
class ContributionPanel extends Panel {

	private static final int MAX_CONTRIBUTORS = 1000;
	
	private final IModel<Depot> depotModel;
	
	private final IModel<RevCommit> commitModel = new LoadableDetachableModel<RevCommit>() {

		@Override
		protected RevCommit load() {
			// call revlist command for performance reason
			RevListCommand command = new RevListCommand(depotModel.getObject().getDirectory());
			command.revisions(Lists.newArrayList(blobIdent.revision));
			if (blobIdent.path != null)
				command.paths(Lists.newArrayList(blobIdent.path));
			command.count(1);
			String commitId = command.call().iterator().next();
			return depotModel.getObject().getRevCommit(commitId);
		}
		
	};
	
	private final BlobIdent blobIdent;
	
	public ContributionPanel(String id, IModel<Depot> depotModel, BlobIdent blobIdent) {
		super(id);
		
		this.depotModel = depotModel;		

		this.blobIdent = blobIdent;
	}

	@Override
	protected void onInitialize() {
		super.onInitialize();
		
		RevCommit commit = commitModel.getObject();
		add(new AvatarLink("avatar", commit.getAuthorIdent()));
		add(new ContributorPanel("contributor", commit.getAuthorIdent(), 
				commit.getCommitterIdent(), false));
		
		Link<Void> link = new BookmarkablePageLink<Void>("messageLink", CommitDetailPage.class, 
				CommitDetailPage.paramsOf(depotModel.getObject(), commit.name()));
		link.add(new Label("message", commit.getShortMessage()));
		add(link);

		add(new Label("date", DateUtils.formatAge(commit.getCommitterIdent().getWhen())));
		
		add(new DropdownLink("contributors") {

			@Override
			protected Component newContent(String id) {
				return new AjaxLazyLoadPanel(id) {

					@Override
					public Component getLoadingComponent(String markupId) {
						return new LoadingIndicator(markupId);
					}

					@Override
					public Component getLazyLoadComponent(String markupId) {
						Fragment fragment = new Fragment(id, "contributorsFrag", ContributionPanel.this);
						fragment.add(new ListView<NameAndEmail>("contributors", new LoadableDetachableModel<List<NameAndEmail>>() {

							@Override
							protected List<NameAndEmail> load() {
								Depot depot = depotModel.getObject();
								RevListCommand revList = new RevListCommand(depot.getDirectory());
								if (blobIdent.path != null)
									revList.paths(Lists.newArrayList(blobIdent.path));
								revList.revisions(Lists.newArrayList(blobIdent.revision));
								revList.count(MAX_CONTRIBUTORS);
								Set<NameAndEmail> setOfAuthors = new HashSet<>();
								try (RevWalk revWalk = new RevWalk(depot.getRepository())) {
									for (String commitHash: revList.call()) {
										RevCommit commit = revWalk.parseCommit(ObjectId.fromString(commitHash));
										setOfAuthors.add(new NameAndEmail(commit.getAuthorIdent()));
									}
								} catch (IOException e) {
									throw new RuntimeException(e);
								}
								List<NameAndEmail> listOfAuthors = new ArrayList<>(setOfAuthors);
								Collections.sort(listOfAuthors);
								return listOfAuthors;
							}
							
						}) {

							@Override
							protected void populateItem(ListItem<NameAndEmail> item) {
								NameAndEmail nameAndEmail = item.getModelObject();
								PersonIdent person = new PersonIdent(nameAndEmail.getName(), nameAndEmail.getEmailAddress());
								DepotCommitsPage.State state = new DepotCommitsPage.State(); 
								state.setCompareWith(blobIdent.revision);
								if (blobIdent.path != null) {
									state.setQuery(String.format("path(%s) author(%s <%s>)", 
											blobIdent.path, nameAndEmail.getName(), nameAndEmail.getEmailAddress()));
								} else {
									state.setQuery(String.format("author(%s <%s>)", 
											nameAndEmail.getName(), nameAndEmail.getEmailAddress()));
								}
								Link<Void> link = new BookmarkablePageLink<Void>("link", DepotCommitsPage.class, 
										DepotCommitsPage.paramsOf(depotModel.getObject(), state));
								link.add(new Avatar("avatar", person));
								link.add(new Label("name", person.getName()));
								item.add(link);
							}
							
						});
						return fragment;
					}
					
				};
			}

		});
		
		setOutputMarkupId(true);
	}

	@Override
	protected void onDetach() {
		commitModel.detach();
		depotModel.detach();
		
		super.onDetach();
	}

}
