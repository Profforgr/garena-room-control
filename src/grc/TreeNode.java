/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
 
package grc;

/**
 *
 * @author GG.Dragon
 */

public class TreeNode {
	
	/* commented out parent values since I'm not using them at the moment, but might in future */
	
	protected UserInfo val; //value associated with node
	//protected TreeNode parent_user; //parent of node based on uid
	protected TreeNode left_uid; //left child of node based on uid
	protected TreeNode right_uid; //right child of node based on uid
	
	//protected TreeNode parent_user; //parent of node based on username
	protected TreeNode left_user; //left child of node based on username
	protected TreeNode right_user; //right child of node based on username
	
	public TreeNode(UserInfo value) {
		//post: returns a tree referencing value with four null subtrees
		val = value;
		left_uid = null;
		right_uid = null;
		left_user = null;
		right_user = null;
	}
	
	public TreeNode() {
		val = null;
		left_uid = right_uid = left_user = right_user = null;
	}
	
	//might use this in future
	/*public TreeNode(Object value, TreeNode left_uid, TreeNode right_uid, TreeNode left_user, TreeNode right_user) {
		//post: returns a node referencing value & subtrees
		this(value);
		setLeft(left);
		setRight(right);
	}*/
	
	/*public TreeNode getParentUid() {
		//post: returns reference to parent_uid node, or null
		return parent_uid;
	}*/
	
	public TreeNode getLeftUid() {
		//post: returns reference to left_uid subtree, or null
		return left_uid;
	}
	
	public TreeNode getRightUid() {
		//post: returns reference to right_uid subtree, or null
		return right_uid;
	}
	
	/*public TreeNode getParentUser() {
		//post: returns reference to parent_user node, or null
		return parent_user;
	}*/
	
	public TreeNode getLeftUser() {
		//post: returns reference to left_user subtree, or null
		return left_user;
	}
	
	public TreeNode getRightUser() {
		//post: returns reference to right_user subtree, or null
		return right_user;
	}
	
	public void setLeftUid(TreeNode newLeft) {
		//post: sets left_uid subtree to newLeft
		left_uid = newLeft;
	}
	
	public void setRightUid(TreeNode newRight) {
		//post: sets right_uid subtree to newRight
		right_uid = newRight;
	}
	
	public void setLeftUser(TreeNode newLeft) {
		//post: sets left_user subtree to newLeft
		left_user = newLeft;
	}
	
	public void setRightUser(TreeNode newRight) {
		//post: sets right_user subtree to newRight
		right_user = newRight;
	}
	
	public UserInfo getValue() {
		//post: returns value associated with this node
		return val;
	}
	
	public void setValue(UserInfo value) {
		//post: sets the value associated with this node
		val = value;
	}
	
	public void clear() {
		//post: clears all subtrees, java will delete the BinaryTreeNodes
		left_uid = null;
		right_uid = null;
		left_user = null;
		right_user = null;
	}
}